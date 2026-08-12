package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.economy.VaultEconomy;
import it.thatsade.unrealauctions.model.CollectionItem;
import it.thatsade.unrealauctions.model.CollectionSource;
import it.thatsade.unrealauctions.model.Order;
import it.thatsade.unrealauctions.model.OrderStatus;
import it.thatsade.unrealauctions.storage.*;
import it.thatsade.unrealauctions.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

public class FulfillOrderTransaction extends Transaction {

    private final OrderRepository orderRepo;
    private final CollectionRepository collectionRepo;
    private final PlayerStatsRepository statsRepo;
    private final VaultEconomy economy;
    private final UUID fulfillerUuid;
    private final String fulfillerName;
    private final UUID orderId;
    private final List<ItemStack> items;
    private final long collectionExpiryMillis;

    private Order order;

    public FulfillOrderTransaction(DatabaseManager db, TransactionLogRepository logRepo,
                                   OrderRepository orderRepo, CollectionRepository collectionRepo,
                                   PlayerStatsRepository statsRepo, VaultEconomy economy,
                                   UUID fulfillerUuid, String fulfillerName, UUID orderId,
                                   List<ItemStack> items, long collectionExpiryMillis) {
        super(db, logRepo);
        this.orderRepo = orderRepo;
        this.collectionRepo = collectionRepo;
        this.statsRepo = statsRepo;
        this.economy = economy;
        this.fulfillerUuid = fulfillerUuid;
        this.fulfillerName = fulfillerName;
        this.orderId = orderId;
        this.items = items;
        this.collectionExpiryMillis = collectionExpiryMillis;
    }

    @Override
    protected TransactionResult doExecute(Connection conn) throws Exception {
        var opt = orderRepo.findById(conn, orderId);
        if (opt.isEmpty() || opt.get().getStatus() != OrderStatus.OPEN) {
            return TransactionResult.failure("order-not-found");
        }
        order = opt.get();

        if (order.isExpired()) {
            return TransactionResult.failure("order-expired-fulfil");
        }
        if (order.getCreatorUuid().equals(fulfillerUuid)) {
            return TransactionResult.failure("cannot-fulfil-own");
        }

        // Defensive re-check: the items were taken from the player before this ran.
        int supplied = 0;
        for (ItemStack stack : items) {
            if (!stack.getType().name().equals(order.getItemType())) {
                return TransactionResult.failure("order-missing-items");
            }
            supplied += stack.getAmount();
        }
        if (supplied != order.getQuantity()) {
            return TransactionResult.failure("order-missing-items");
        }

        long now = System.currentTimeMillis();
        orderRepo.updateStatus(conn, orderId, OrderStatus.FULFILLED,
                fulfillerUuid, fulfillerName, now);

        // Deliver the goods to the order creator's collection box (they may be offline).
        for (ItemStack stack : items) {
            CollectionItem delivered = CollectionItem.builder()
                    .id(UUID.randomUUID())
                    .ownerUuid(order.getCreatorUuid())
                    .item(stack)
                    .itemData(ItemSerializer.serialize(stack))
                    .source(CollectionSource.ORDER_FULFILLED)
                    .sourceListingId(orderId)
                    .addedAt(now)
                    .expiresAt(now + collectionExpiryMillis)
                    .build();
            collectionRepo.insert(conn, delivered);
        }

        statsRepo.incrementSale(conn, fulfillerUuid, order.getTotalPrice());
        statsRepo.incrementPurchase(conn, order.getCreatorUuid(), order.getTotalPrice());

        return TransactionResult.success(order);
    }

    @Override
    protected TransactionResult executePostCommit(TransactionResult dbResult) {
        // Release the escrowed payment to the fulfiller. The creator was already
        // debited at order creation, so there is nobody to refund if this fails —
        // log loudly so an admin can settle it manually.
        if (!economy.deposit(fulfillerUuid, order.getTotalPrice())) {
            log.severe("Failed to pay " + order.getTotalPrice() + " to " + fulfillerUuid
                    + " for fulfilling order " + orderId
                    + ". Items were delivered; payment needs manual settlement.");
            try (Connection conn = db.getConnection()) {
                logRepo.log(conn, "FULFILL_ORDER", fulfillerUuid, orderId,
                        order.getTotalPrice(), "ECONOMY_FAIL", "Escrow payout failed");
            } catch (Exception e) {
                log.warning("Failed to write transaction log: " + e.getMessage());
            }
            return dbResult;
        }

        try (Connection conn = db.getConnection()) {
            logRepo.log(conn, "FULFILL_ORDER", fulfillerUuid, orderId,
                    order.getTotalPrice(), "SUCCESS", null);
        } catch (Exception e) {
            log.warning("Failed to write transaction log: " + e.getMessage());
        }

        return dbResult;
    }
}
