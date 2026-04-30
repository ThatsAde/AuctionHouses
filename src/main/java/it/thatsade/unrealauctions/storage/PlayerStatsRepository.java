package it.thatsade.unrealauctions.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerStatsRepository {

    private final boolean mysql;

    public PlayerStatsRepository(DatabaseManager db) {
        this.mysql = db.isMysql();
    }

    public void incrementListings(Connection conn, UUID playerUuid) throws SQLException {
        long now = System.currentTimeMillis();
        if (mysql) {
            String sql = """
                    INSERT INTO player_stats (player_uuid, total_listings, last_listing_at)
                    VALUES (?, 1, ?)
                    ON DUPLICATE KEY UPDATE total_listings = total_listings + 1, last_listing_at = ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, now);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        } else {
            String sql = """
                    INSERT INTO player_stats (player_uuid, total_listings, last_listing_at)
                    VALUES (?, 1, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        total_listings = total_listings + 1,
                        last_listing_at = excluded.last_listing_at
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, now);
                ps.executeUpdate();
            }
        }
    }

    public void incrementSale(Connection conn, UUID sellerUuid, long amount) throws SQLException {
        if (mysql) {
            String sql = """
                    INSERT INTO player_stats (player_uuid, total_sales, total_earned)
                    VALUES (?, 1, ?)
                    ON DUPLICATE KEY UPDATE total_sales = total_sales + 1, total_earned = total_earned + ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sellerUuid.toString());
                ps.setLong(2, amount);
                ps.setLong(3, amount);
                ps.executeUpdate();
            }
        } else {
            String sql = """
                    INSERT INTO player_stats (player_uuid, total_sales, total_earned)
                    VALUES (?, 1, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        total_sales = total_sales + 1,
                        total_earned = total_earned + excluded.total_earned
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sellerUuid.toString());
                ps.setLong(2, amount);
                ps.executeUpdate();
            }
        }
    }

    public void incrementPurchase(Connection conn, UUID buyerUuid, long amount) throws SQLException {
        if (mysql) {
            String sql = """
                    INSERT INTO player_stats (player_uuid, total_purchases, total_spent)
                    VALUES (?, 1, ?)
                    ON DUPLICATE KEY UPDATE total_purchases = total_purchases + 1, total_spent = total_spent + ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, buyerUuid.toString());
                ps.setLong(2, amount);
                ps.setLong(3, amount);
                ps.executeUpdate();
            }
        } else {
            String sql = """
                    INSERT INTO player_stats (player_uuid, total_purchases, total_spent)
                    VALUES (?, 1, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        total_purchases = total_purchases + 1,
                        total_spent = total_spent + excluded.total_spent
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, buyerUuid.toString());
                ps.setLong(2, amount);
                ps.executeUpdate();
            }
        }
    }
}
