package it.thatsade.unrealauctions.service;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.economy.VaultEconomy;
import it.thatsade.unrealauctions.model.CollectionItem;
import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.model.Order;
import it.thatsade.unrealauctions.model.OrderStatus;
import it.thatsade.unrealauctions.storage.*;
import it.thatsade.unrealauctions.transaction.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class AuctionService {

    private static final Logger log = Logger.getLogger("UnrealAuctions");

    private final Plugin plugin;
    private final Config config;
    private final TransactionQueue txQueue;
    private final DatabaseManager db;
    private final ListingRepository listingRepo;
    private final CollectionRepository collectionRepo;
    private final OrderRepository orderRepo;
    private final TransactionLogRepository logRepo;
    private final PlayerStatsRepository statsRepo;
    private final VaultEconomy economy;
    private final ValidationService validator;
    private final RateLimiter rateLimiter;

    public AuctionService(Plugin plugin, Config config, TransactionQueue txQueue,
                          DatabaseManager db, ListingRepository listingRepo,
                          CollectionRepository collectionRepo, OrderRepository orderRepo,
                          TransactionLogRepository logRepo,
                          PlayerStatsRepository statsRepo, VaultEconomy economy,
                          ValidationService validator, RateLimiter rateLimiter) {
        this.plugin = plugin;
        this.config = config;
        this.txQueue = txQueue;
        this.db = db;
        this.listingRepo = listingRepo;
        this.collectionRepo = collectionRepo;
        this.orderRepo = orderRepo;
        this.logRepo = logRepo;
        this.statsRepo = statsRepo;
        this.economy = economy;
        this.validator = validator;
        this.rateLimiter = rateLimiter;
    }

    // ── Write operations (go through the queue) ───────────────────────────────

    /**
     * Returns the effective max-active-listings limit for this player.
     * Iterates all configured permission-limits and returns the highest limit
     * the player holds. -1 means unlimited. Falls back to the default.
     * Must be called on the main thread (permission checks require it).
     */
    public int resolveListingLimit(Player player) {
        return resolveLimit(player, config.getMaxActivePerPlayer(), config.getPermissionLimits());
    }

    /**
     * Returns the effective max-open-orders limit for this player, resolved the
     * same way as the listing limit. -1 means unlimited.
     * Must be called on the main thread (permission checks require it).
     */
    public int resolveOrderLimit(Player player) {
        return resolveLimit(player, config.getMaxOpenOrdersPerPlayer(),
                config.getOrderPermissionLimits());
    }

    private int resolveLimit(Player player, int base, Map<String, Integer> permissionLimits) {
        int limit = base;
        for (Map.Entry<String, Integer> entry : permissionLimits.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                int permLimit = entry.getValue();
                if (permLimit == -1) return -1; // unlimited — short-circuit
                limit = Math.max(limit, permLimit);
            }
        }
        return limit;
    }

    /**
     * Lists {@code amount} items from the supplied held stack. Returns false if
     * the queue is full. The callback runs on the main thread.
     */
    public boolean listItem(Player player, ItemStack item, int amount, long price,
                            Consumer<TransactionResult> callback) {
        ItemStack listingItem = item.clone();
        listingItem.setAmount(amount);

        ValidationResult validation = validator.validateListing(listingItem, price);
        if (validation.isInvalid()) {
            callback.accept(TransactionResult.failure(validation.messageKey()));
            return true;
        }

        if (!rateLimiter.checkListingAllowed(player.getUniqueId())) {
            long remaining = rateLimiter.getCooldownRemaining(player.getUniqueId());
            callback.accept(TransactionResult.failure(
                    remaining > 0 ? "cooldown" : "rate-limit-listings"));
            return true;
        }

        rateLimiter.recordListing(player.getUniqueId());

        // Resolve limit on main thread — permission checks cannot run async.
        int maxListings = resolveListingLimit(player);

        Transaction tx = new ListItemTransaction(
                db, logRepo, listingRepo, statsRepo, economy,
                player.getUniqueId(), player.getName(), listingItem, price, config, maxListings
        );

        boolean submitted = txQueue.submit(tx, result -> {
            if (!result.isSuccess()) {
                returnItems(player, List.of(listingItem));
            }
            callback.accept(result);
        });

        if (submitted) {
            // Escrow the items immediately, before the player can swap slots or
            // otherwise move them while the database transaction is queued.
            if (item.getAmount() == amount) {
                player.getInventory().setItemInMainHand(null);
            } else {
                item.setAmount(item.getAmount() - amount);
            }
        }
        return submitted;
    }

    /**
     * Submits a purchase. Fails immediately if the player's inventory has no space.
     * On success the item is given directly to the player's inventory.
     */
    public boolean buyItem(Player player, UUID listingId, Consumer<TransactionResult> callback) {
        if (!rateLimiter.checkPurchaseAllowed(player.getUniqueId())) {
            callback.accept(TransactionResult.failure("rate-limit-purchases"));
            return true;
        }

        Transaction tx = new BuyItemTransaction(
                db, logRepo, listingRepo, statsRepo, economy,
                player.getUniqueId(), listingId
        );

        return txQueue.submit(tx, result -> {
            if (result.isSuccess()) {
                Listing purchased = result.getPayload(Listing.class);
                ItemStack item = purchased.getItem();
                if (item != null) {
                    if (!canFitItem(player, item)) {
                        // Rare race: inventory filled between submit and callback — drop at feet
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        log.warning("Inventory full on purchase callback for " + player.getName()
                                + " (listing " + listingId + "); item dropped.");
                    } else {
                        player.getInventory().addItem(item);
                        rateLimiter.recordPurchase(player.getUniqueId());
                    }
                }
            }
            callback.accept(result);
        });
    }

    /** Returns true if item can fit in the player's inventory (empty slot or stackable). */
    public boolean canFitItem(Player player, ItemStack item) {
        var inv = player.getInventory();
        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) return true;
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) return true;
        }
        return false;
    }

    /**
     * Cancels a listing. On success the item is moved to the seller's collection box.
     */
    public boolean cancelListing(Player player, UUID listingId, Consumer<TransactionResult> callback) {
        Transaction tx = new CancelListingTransaction(
                db, logRepo, listingRepo, collectionRepo, economy,
                player.getUniqueId(), listingId, false,
                config.getCancelFeePercent(), config.getCollectionBoxExpiryMillis()
        );
        return txQueue.submit(tx, callback);
    }

    /**
     * Admin cancel — bypasses ownership check.
     */
    public boolean adminCancelListing(UUID listingId, Consumer<TransactionResult> callback) {
        Transaction tx = new CancelListingTransaction(
                db, logRepo, listingRepo, collectionRepo, economy,
                UUID.randomUUID(), listingId, true, 0.0,
                config.getCollectionBoxExpiryMillis()
        );
        return txQueue.submit(tx, callback);
    }

    /** Deletes collection box items whose expiry has passed. Safe to call async. */
    public void purgeExpiredCollectionItems() {
        // Skip while the DB is known to be down — avoids a doomed connection
        // attempt and repeated warnings every 5 minutes during an outage.
        if (txQueue.isDatabaseDown()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                int purged = collectionRepo.deleteExpired(conn);
                if (purged > 0) {
                    log.info("Purged " + purged + " expired collection box item(s).");
                }
            } catch (SQLException e) {
                log.warning("Failed to purge expired collection items: " + e.getMessage());
            }
        });
    }

    /**
     * Collects a collection box item.
     * Caller MUST check inventory space before calling.
     * On success, give item to player in callback.
     */
    public boolean collectItem(Player player, UUID collectionItemId,
                               Consumer<TransactionResult> callback) {
        Transaction tx = new CollectItemTransaction(
                db, logRepo, collectionRepo, player.getUniqueId(), collectionItemId
        );
        return txQueue.submit(tx, result -> {
            if (result.isSuccess()) {
                CollectionItem ci = result.getPayload(CollectionItem.class);
                ItemStack item = ci.getItem();
                if (item != null) {
                    if (player.getInventory().firstEmpty() == -1) {
                        // Inventory filled between check and callback — drop at feet
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        log.warning("Inventory full during collect for " + player.getName()
                                + "; item dropped at location.");
                    } else {
                        player.getInventory().addItem(item);
                    }
                }
            }
            callback.accept(result);
        });
    }

    // ── Item orders ───────────────────────────────────────────────────────────

    /**
     * Creates an item order. The offer is escrowed on success.
     * Returns false if the queue is full.
     */
    public boolean createOrder(Player player, Material material, int quantity, long totalPrice,
                               Consumer<TransactionResult> callback) {
        ValidationResult validation = validator.validatePrice(totalPrice);
        if (validation.isInvalid()) {
            callback.accept(TransactionResult.failure(validation.messageKey()));
            return true;
        }

        if (!rateLimiter.checkListingAllowed(player.getUniqueId())) {
            long remaining = rateLimiter.getCooldownRemaining(player.getUniqueId());
            callback.accept(TransactionResult.failure(
                    remaining > 0 ? "cooldown" : "rate-limit-listings"));
            return true;
        }

        rateLimiter.recordListing(player.getUniqueId());

        Transaction tx = new CreateOrderTransaction(
                db, logRepo, orderRepo, economy,
                player.getUniqueId(), player.getName(), material.name(), quantity, totalPrice,
                resolveOrderLimit(player), config.getOrderDurationHours()
        );
        return txQueue.submit(tx, callback);
    }

    /**
     * Fills an order using items from the player's inventory.
     * The items are taken up front and returned if the transaction fails, so the
     * player can never end up paid without having handed the goods over.
     */
    public boolean fulfillOrder(Player player, Order order, Consumer<TransactionResult> callback) {
        Material material = order.getMaterial();
        if (material == null || countMatchingItems(player, material) < order.getQuantity()) {
            callback.accept(TransactionResult.failure("order-missing-items"));
            return true;
        }

        List<ItemStack> taken = removeMatchingItems(player, material, order.getQuantity());

        Transaction tx = new FulfillOrderTransaction(
                db, logRepo, orderRepo, collectionRepo, statsRepo, economy,
                player.getUniqueId(), player.getName(), order.getId(), taken,
                config.getCollectionBoxExpiryMillis()
        );

        boolean submitted = txQueue.submit(tx, result -> {
            if (!result.isSuccess()) {
                returnItems(player, taken);
            }
            callback.accept(result);
        });

        if (!submitted) {
            returnItems(player, taken);
        }
        return submitted;
    }

    /** Cancels an open order and refunds the escrowed offer. */
    public boolean cancelOrder(Player player, UUID orderId, Consumer<TransactionResult> callback) {
        Transaction tx = new CancelOrderTransaction(
                db, logRepo, orderRepo, economy, player.getUniqueId(), orderId, false
        );
        return txQueue.submit(tx, callback);
    }

    /** Counts inventory items of this material that are plain enough to fill an order. */
    public int countMatchingItems(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isPlainItem(stack, material)) total += stack.getAmount();
        }
        return total;
    }

    /**
     * An item only counts towards an order if it is a plain, unmodified copy —
     * otherwise a near-broken or cursed item could be passed off as the real thing.
     */
    private boolean isPlainItem(ItemStack stack, Material material) {
        if (stack == null || stack.getType() != material) return false;
        if (!stack.getEnchantments().isEmpty()) return false;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return true;
        if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants()) return false;
        if (meta.hasCustomModelData()) return false;
        return !(meta instanceof Damageable damageable) || !damageable.hasDamage();
    }

    private List<ItemStack> removeMatchingItems(Player player, Material material, int quantity) {
        List<ItemStack> removed = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        int remaining = quantity;

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!isPlainItem(stack, material)) continue;

            int take = Math.min(remaining, stack.getAmount());
            ItemStack taken = stack.clone();
            taken.setAmount(take);
            removed.add(taken);

            if (stack.getAmount() == take) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
            remaining -= take;
        }

        player.getInventory().setStorageContents(contents);
        return removed;
    }

    private void returnItems(Player player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    public void fetchOpenOrders(int page, int itemsPerPage, Consumer<List<Order>> callback) {
        int offset = page * itemsPerPage;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                List<Order> orders = orderRepo.findOpenOrders(conn, offset, itemsPerPage);
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(orders));
            } catch (SQLException e) {
                log.warning("Failed to fetch open orders: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(List.of()));
            }
        });
    }

    public void fetchOpenOrderCount(Consumer<Integer> callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                int count = orderRepo.countOpenOrders(conn);
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(count));
            } catch (SQLException e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(0));
            }
        });
    }

    public void fetchPlayerOrders(UUID playerUuid, Consumer<List<Order>> callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                List<Order> orders = orderRepo.findByCreatorAndStatus(conn, playerUuid, OrderStatus.OPEN);
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(orders));
            } catch (SQLException e) {
                log.warning("Failed to fetch player orders: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(List.of()));
            }
        });
    }

    // ── Read operations (async, no queue needed) ──────────────────────────────

    public void fetchActiveListings(int page, int itemsPerPage,
                                    Consumer<List<Listing>> callback) {
        int offset = page * itemsPerPage;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                List<Listing> listings = listingRepo.findActiveListings(conn, offset, itemsPerPage);
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(listings));
            } catch (SQLException e) {
                log.warning("Failed to fetch active listings: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(List.of()));
            }
        });
    }

    public void fetchActiveListingCount(Consumer<Integer> callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                int count = listingRepo.countActiveListings(conn);
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(count));
            } catch (SQLException e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(0));
            }
        });
    }

    public void fetchPlayerListings(UUID playerUuid, Consumer<List<Listing>> callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                List<Listing> listings = listingRepo.findBySellerAndStatus(
                        conn, playerUuid, it.thatsade.unrealauctions.model.ListingStatus.ACTIVE);
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(listings));
            } catch (SQLException e) {
                log.warning("Failed to fetch player listings: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(List.of()));
            }
        });
    }

    public void fetchCollectionBox(UUID playerUuid, Consumer<List<CollectionItem>> callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                List<CollectionItem> items = collectionRepo.findByOwner(conn, playerUuid);
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(items));
            } catch (SQLException e) {
                log.warning("Failed to fetch collection box: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(List.of()));
            }
        });
    }

    public int getQueueDepth() {
        return txQueue.getDepth();
    }

    public String getFormattedBalance(UUID playerUuid) {
        return economy.format(economy.getBalance(playerUuid));
    }
}
