package it.thatsade.unrealauctions.gui;

import it.thatsade.unrealauctions.UnrealAuctionsPlugin;
import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.service.AuctionService;
import org.bukkit.plugin.java.JavaPlugin;
import it.thatsade.unrealauctions.util.PriceFormatter;
import it.thatsade.unrealauctions.util.TimeFormatter;
import static it.thatsade.unrealauctions.gui.PaginatedGUI.noItalic;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainBrowseGUI extends PaginatedGUI {

    // Bottom row slot positions for this GUI
    private static final int SLOT_PREV_PAGE    = 45;
    private static final int SLOT_ORDERS       = 47;
    private static final int SLOT_MY_LISTINGS  = 48;
    private static final int SLOT_PLAYER_INFO  = 49;
    private static final int SLOT_COLLECTION   = 50;
    private static final int SLOT_NEXT_PAGE    = 53;

    private final AuctionService auctionService;
    private final Config config;
    private final Map<Integer, Listing> slotToListing = new HashMap<>();
    private boolean loading = false;

    public MainBrowseGUI(Player player, Messages messages, AuctionService auctionService, Config config) {
        super(player, messages);
        this.auctionService = auctionService;
        this.config = config;
    }

    @Override
    protected String getTitleKey() {
        return "gui-browse-title";
    }

    @Override
    protected void populate() {
        slotToListing.clear();
        setLoadingState();
        loading = true;

        auctionService.fetchActiveListingCount(count -> {
            if (!isOpen()) return;
            int perPage = config.getItemsPerPage();
            totalPages = Math.max(1, (int) Math.ceil((double) count / perPage));
            if (currentPage >= totalPages) currentPage = totalPages - 1;

            auctionService.fetchActiveListings(currentPage, perPage, listings -> {
                loading = false;
                if (!isOpen()) return;
                slotToListing.clear();

                // Clear content area — leave top 5 rows empty
                for (int i = 0; i < CONTENT_SLOTS; i++) {
                    inventory.setItem(i, null);
                }

                // Place listings in content slots (0-44)
                for (int i = 0; i < listings.size() && i < CONTENT_SLOTS; i++) {
                    Listing listing = listings.get(i);
                    inventory.setItem(i, buildListingDisplay(listing));
                    slotToListing.put(i, listing);
                }

                // Bottom row controls
                fillBottomRow();
            });
        });
    }

    private void setLoadingState() {
        // Fill everything with glass, put a clock in the center of the bottom row
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(49, buildItem(Material.CLOCK, "&7Loading..."));
    }

    private void fillBottomRow() {
        // Solid glass backing so unused control slots never show as empty holes.
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s <= 53; s++) inventory.setItem(s, filler);

        // Prev page
        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV_PAGE, buildNavItem(Material.ARROW,
                    messages.getNoPrefix("btn-prev-page"), true));
        }

        // Item Orders (Writable Book)
        if (config.isOrdersEnabled()) {
            inventory.setItem(SLOT_ORDERS, buildItem(Material.WRITABLE_BOOK,
                    messages.getNoPrefix("btn-orders")));
        }

        // My Listings (Chest)
        inventory.setItem(SLOT_MY_LISTINGS, buildItem(Material.CHEST,
                messages.getNoPrefix("btn-my-listings")));

        // Player head with name + balance
        inventory.setItem(SLOT_PLAYER_INFO, buildPlayerInfoItem());

        // Collection Box (Ender Chest)
        inventory.setItem(SLOT_COLLECTION, buildItem(Material.ENDER_CHEST,
                messages.getNoPrefix("btn-collection-box")));

        // Next page
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT_PAGE, buildNavItem(Material.ARROW,
                    messages.getNoPrefix("btn-next-page"), true));
        }
    }

    private ItemStack buildPlayerInfoItem() {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(noItalic(messages.getNoPrefix("btn-player-name", Map.of("name", player.getName()))));

        String balance = auctionService.getFormattedBalance(player.getUniqueId());
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(noItalic(messages.getNoPrefix("btn-player-balance", Map.of("balance", balance))));
        meta.lore(lore);

        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack buildListingDisplay(Listing listing) {
        ItemStack display = listing.getItem() != null
                ? listing.getItem().clone()
                : new ItemStack(Material.BARRIER);
        ItemMeta meta = display.getItemMeta();

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(noItalic(messages.getNoPrefix("lore-seller", Map.of("seller", listing.getSellerName()))));
        lore.add(noItalic(messages.getNoPrefix("lore-price",
                Map.of("price", PriceFormatter.format(listing.getPrice(), config.getCurrencySymbol())))));
        lore.add(noItalic(messages.getNoPrefix("lore-time-left",
                Map.of("time", TimeFormatter.formatDuration(listing.getMillisRemaining())))));
        lore.add(noItalic(messages.getNoPrefix("lore-left-click-buy")));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (loading) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == SLOT_PREV_PAGE && currentPage > 0) {
            currentPage--;
            refresh();
            return;
        }
        if (slot == SLOT_NEXT_PAGE && currentPage < totalPages - 1) {
            currentPage++;
            refresh();
            return;
        }
        if (slot == SLOT_ORDERS) {
            if (!config.isOrdersEnabled()) return;
            if (!player.hasPermission("unrealauctions.orders")) {
                messages.send(player, "no-permission");
                return;
            }
            new OrderBrowseGUI(player, messages, auctionService, config,
                    JavaPlugin.getPlugin(UnrealAuctionsPlugin.class).getOrderDraftService()).open();
            return;
        }
        if (slot == SLOT_MY_LISTINGS) {
            new MyListingsGUI(player, messages, auctionService, config).open();
            return;
        }
        if (slot == SLOT_COLLECTION) {
            new CollectionBoxGUI(player, messages, auctionService, config).open();
            return;
        }

        // Listing click (content area only)
        if (slot >= CONTENT_SLOTS) return;
        Listing listing = slotToListing.get(slot);
        if (listing == null) return;

        if (event.getClick() == ClickType.LEFT) {
            new BuyConfirmGUI(player, messages, auctionService, config, listing).open();
        }
    }
}
