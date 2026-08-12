package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.economy.VaultEconomy;
import it.thatsade.unrealauctions.model.Order;
import it.thatsade.unrealauctions.model.OrderStatus;
import it.thatsade.unrealauctions.storage.DatabaseManager;
import it.thatsade.unrealauctions.storage.OrderRepository;
import it.thatsade.unrealauctions.storage.TransactionLogRepository;

import java.sql.Connection;
import java.util.UUID;

public class CancelOrderTransaction extends Transaction {

    private final OrderRepository orderRepo;
    private final VaultEconomy economy;
    private final UUID requesterUuid;
    private final UUID orderId;
    private final boolean isAdmin;

    private Order order;

    public CancelOrderTransaction(DatabaseManager db, TransactionLogRepository logRepo,
                                  OrderRepository orderRepo, VaultEconomy economy,
                                  UUID requesterUuid, UUID orderId, boolean isAdmin) {
        super(db, logRepo);
        this.orderRepo = orderRepo;
        this.economy = economy;
        this.requesterUuid = requesterUuid;
        this.orderId = orderId;
        this.isAdmin = isAdmin;
    }

    @Override
    protected TransactionResult doExecute(Connection conn) throws Exception {
        var opt = orderRepo.findById(conn, orderId);
        if (opt.isEmpty() || opt.get().getStatus() != OrderStatus.OPEN) {
            return TransactionResult.failure("order-not-found");
        }
        order = opt.get();

        if (!isAdmin && !order.getCreatorUuid().equals(requesterUuid)) {
            return TransactionResult.failure("no-permission");
        }

        orderRepo.updateStatus(conn, orderId, OrderStatus.CANCELLED, null, null, null);

        return TransactionResult.success(order);
    }

    @Override
    protected TransactionResult executePostCommit(TransactionResult dbResult) {
        // Return the escrowed payment to the creator.
        if (!economy.deposit(order.getCreatorUuid(), order.getTotalPrice())) {
            log.severe("Failed to refund escrow of " + order.getTotalPrice() + " to "
                    + order.getCreatorUuid() + " for cancelled order " + orderId
                    + ". Needs manual settlement.");
            try (Connection conn = db.getConnection()) {
                logRepo.log(conn, "CANCEL_ORDER", requesterUuid, orderId,
                        order.getTotalPrice(), "ECONOMY_FAIL", "Escrow refund failed");
            } catch (Exception e) {
                log.warning("Failed to write transaction log: " + e.getMessage());
            }
            return dbResult;
        }

        try (Connection conn = db.getConnection()) {
            logRepo.log(conn, "CANCEL_ORDER", requesterUuid, orderId,
                    order.getTotalPrice(), "SUCCESS", null);
        } catch (Exception e) {
            log.warning("Failed to write transaction log: " + e.getMessage());
        }

        return dbResult;
    }
}
