package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.storage.DatabaseManager;
import it.thatsade.unrealauctions.storage.TransactionLogRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Transaction {

    protected static final Logger log = Logger.getLogger("UnrealAuctions");

    protected final DatabaseManager db;
    protected final TransactionLogRepository logRepo;

    protected Transaction(DatabaseManager db, TransactionLogRepository logRepo) {
        this.db = db;
        this.logRepo = logRepo;
    }

    /**
     * Executes the transaction: opens a connection, runs doExecute() in a DB transaction,
     * commits on success (rolls back on failure), then runs post-commit economy operations.
     */
    public final TransactionResult execute() {
        TransactionResult dbResult;

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                dbResult = doExecute(conn);
                if (dbResult.isSuccess()) {
                    conn.commit();
                } else {
                    conn.rollback();
                }
            } catch (Exception e) {
                safeRollback(conn);
                log.log(Level.SEVERE, "Transaction threw an exception: " + e.getMessage(), e);
                dbResult = TransactionResult.failure("internal-error");
            }
        } catch (SQLException e) {
            // Could not acquire a connection — the DB is unreachable. Don't log a
            // full stack trace here (it would flood the console every cycle while
            // the DB is down); the TransactionQueue throttles/logs this once.
            return TransactionResult.connectionFailure();
        }

        if (dbResult.isSuccess()) {
            return executePostCommit(dbResult);
        }
        return dbResult;
    }

    /** DB-only work inside a transaction. Return failure to trigger rollback. */
    protected abstract TransactionResult doExecute(Connection conn) throws Exception;

    /** Override for post-commit economy operations (runs outside DB transaction). */
    protected TransactionResult executePostCommit(TransactionResult dbResult) {
        return dbResult;
    }

    private void safeRollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            log.log(Level.WARNING, "Failed to rollback transaction", e);
        }
    }
}
