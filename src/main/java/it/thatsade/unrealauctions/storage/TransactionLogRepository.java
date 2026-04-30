package it.thatsade.unrealauctions.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

public class TransactionLogRepository {

    public TransactionLogRepository(DatabaseManager db) {}

    public void log(Connection conn, String type, UUID actor, UUID listingId,
                    Long amount, String result, String errorMessage) throws SQLException {
        String sql = """
                INSERT INTO transaction_log
                    (id, transaction_type, actor_uuid, listing_id, amount, result, error_message, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, type);
            ps.setString(3, actor.toString());
            if (listingId != null) {
                ps.setString(4, listingId.toString());
            } else {
                ps.setNull(4, Types.CHAR);
            }
            if (amount != null) {
                ps.setLong(5, amount);
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.setString(6, result);
            if (errorMessage != null) {
                ps.setString(7, errorMessage.length() > 255
                        ? errorMessage.substring(0, 255) : errorMessage);
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
