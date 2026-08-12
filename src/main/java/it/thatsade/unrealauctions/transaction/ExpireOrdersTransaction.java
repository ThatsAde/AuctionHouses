package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.economy.VaultEconomy;
import it.thatsade.unrealauctions.model.Order;
import it.thatsade.unrealauctions.model.OrderStatus;
import it.thatsade.unrealauctions.storage.DatabaseManager;
import it.thatsade.unrealauctions.storage.OrderRepository;
import it.thatsade.unrealauctions.storage.TransactionLogRepository;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class ExpireOrdersTransaction extends Transaction {

    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepo;
    private final VaultEconomy economy;

    private final List<Order> refundQueue = new ArrayList<>();

    public ExpireOrdersTransaction(DatabaseManager db, TransactionLogRepository logRepo,
                                   OrderRepository orderRepo, VaultEconomy economy) {
        super(db, logRepo);
        this.orderRepo = orderRepo;
        this.economy = economy;
    }

    @Override
    protected TransactionResult doExecute(Connection conn) throws Exception {
        List<Order> expired = orderRepo.findExpired(conn);
        if (expired.isEmpty()) return TransactionResult.success(0);

        for (int i = 0; i < expired.size(); i += BATCH_SIZE) {
            List<Order> batch = expired.subList(i, Math.min(i + BATCH_SIZE, expired.size()));
            for (Order order : batch) {
                orderRepo.updateStatus(conn, order.getId(), OrderStatus.EXPIRED, null, null, null);
                refundQueue.add(order);
            }
        }

        log.info("Expired " + refundQueue.size() + " item order(s).");
        return TransactionResult.success(refundQueue.size());
    }

    @Override
    protected TransactionResult executePostCommit(TransactionResult dbResult) {
        // Return each creator's escrowed payment now the order is dead.
        for (Order order : refundQueue) {
            if (!economy.deposit(order.getCreatorUuid(), order.getTotalPrice())) {
                log.severe("Failed to refund escrow of " + order.getTotalPrice() + " to "
                        + order.getCreatorUuid() + " for expired order " + order.getId()
                        + ". Needs manual settlement.");
            }
        }

        try (Connection conn = db.getConnection()) {
            for (Order order : refundQueue) {
                logRepo.log(conn, "EXPIRE_ORDER", order.getCreatorUuid(), order.getId(),
                        order.getTotalPrice(), "SUCCESS", null);
            }
        } catch (Exception e) {
            log.warning("Failed to write transaction log: " + e.getMessage());
        }

        return dbResult;
    }
}
