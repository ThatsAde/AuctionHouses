package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.economy.VaultEconomy;
import it.thatsade.unrealauctions.model.Order;
import it.thatsade.unrealauctions.model.OrderStatus;
import it.thatsade.unrealauctions.storage.DatabaseManager;
import it.thatsade.unrealauctions.storage.OrderRepository;
import it.thatsade.unrealauctions.storage.TransactionLogRepository;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CreateOrderTransaction extends Transaction {

    private final OrderRepository orderRepo;
    private final VaultEconomy economy;
    private final UUID creatorUuid;
    private final String creatorName;
    private final String itemType;
    private final int quantity;
    private final long totalPrice;
    private final int maxOpenOrders;
    private final int durationHours;

    private Order createdOrder;

    public CreateOrderTransaction(DatabaseManager db, TransactionLogRepository logRepo,
                                  OrderRepository orderRepo, VaultEconomy economy,
                                  UUID creatorUuid, String creatorName, String itemType,
                                  int quantity, long totalPrice, int maxOpenOrders,
                                  int durationHours) {
        super(db, logRepo);
        this.orderRepo = orderRepo;
        this.economy = economy;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.itemType = itemType;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.maxOpenOrders = maxOpenOrders;
        this.durationHours = durationHours;
    }

    @Override
    protected TransactionResult doExecute(Connection conn) throws Exception {
        int openCount = orderRepo.countOpenByPlayer(conn, creatorUuid);
        if (maxOpenOrders != -1 && openCount >= maxOpenOrders) {
            return TransactionResult.failure("max-orders");
        }

        if (!economy.hasBalance(creatorUuid, totalPrice)) {
            return TransactionResult.failure("not-enough-funds");
        }

        long now = System.currentTimeMillis();
        createdOrder = Order.builder()
                .id(UUID.randomUUID())
                .creatorUuid(creatorUuid)
                .creatorName(creatorName)
                .itemType(itemType)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .status(OrderStatus.OPEN)
                .createdAt(now)
                .expiresAt(now + TimeUnit.HOURS.toMillis(durationHours))
                .build();

        orderRepo.insert(conn, createdOrder);

        return TransactionResult.success(createdOrder);
    }

    @Override
    protected TransactionResult executePostCommit(TransactionResult dbResult) {
        // Escrow: hold the creator's payment for the lifetime of the order.
        if (!economy.withdraw(creatorUuid, totalPrice)) {
            // An open order that was never funded would be free money — void it.
            log.severe("Failed to escrow " + totalPrice + " from " + creatorUuid
                    + " for order " + createdOrder.getId() + ". Cancelling the order.");
            try (Connection conn = db.getConnection()) {
                orderRepo.updateStatus(conn, createdOrder.getId(),
                        OrderStatus.CANCELLED, null, null, null);
                logRepo.log(conn, "CREATE_ORDER", creatorUuid, createdOrder.getId(),
                        totalPrice, "ECONOMY_FAIL", "Escrow withdrawal failed");
            } catch (Exception e) {
                log.severe("Failed to void unfunded order " + createdOrder.getId()
                        + ": " + e.getMessage());
            }
            return TransactionResult.failure("not-enough-funds");
        }

        try (Connection conn = db.getConnection()) {
            logRepo.log(conn, "CREATE_ORDER", creatorUuid, createdOrder.getId(),
                    totalPrice, "SUCCESS", null);
        } catch (Exception e) {
            log.warning("Failed to write transaction log: " + e.getMessage());
        }

        return dbResult;
    }
}
