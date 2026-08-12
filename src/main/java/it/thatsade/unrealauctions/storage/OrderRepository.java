package it.thatsade.unrealauctions.storage;

import it.thatsade.unrealauctions.model.Order;
import it.thatsade.unrealauctions.model.OrderStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrderRepository {

    private final DatabaseManager db;

    public OrderRepository(DatabaseManager db) {
        this.db = db;
    }

    public void insert(Connection conn, Order order) throws SQLException {
        String sql = """
                INSERT INTO orders
                    (id, creator_uuid, creator_name, item_type, quantity, total_price,
                     status, created_at, expires_at, fulfiller_uuid, fulfiller_name, fulfilled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, order.getId().toString());
            ps.setString(2, order.getCreatorUuid().toString());
            ps.setString(3, order.getCreatorName());
            ps.setString(4, order.getItemType());
            ps.setInt(5, order.getQuantity());
            ps.setLong(6, order.getTotalPrice());
            ps.setByte(7, order.getStatus().getCode());
            ps.setLong(8, order.getCreatedAt());
            ps.setLong(9, order.getExpiresAt());
            if (order.getFulfillerUuid() != null) {
                ps.setString(10, order.getFulfillerUuid().toString());
            } else {
                ps.setNull(10, Types.CHAR);
            }
            if (order.getFulfillerName() != null) {
                ps.setString(11, order.getFulfillerName());
            } else {
                ps.setNull(11, Types.VARCHAR);
            }
            if (order.getFulfilledAt() != null) {
                ps.setLong(12, order.getFulfilledAt());
            } else {
                ps.setNull(12, Types.BIGINT);
            }
            ps.executeUpdate();
        }
    }

    public Optional<Order> findById(Connection conn, UUID id) throws SQLException {
        String sql = "SELECT * FROM orders WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public List<Order> findOpenOrders(Connection conn, int offset, int limit) throws SQLException {
        String sql = """
                SELECT * FROM orders
                WHERE status = 0 AND expires_at > ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;
        List<Order> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    public int countOpenOrders(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM orders WHERE status = 0 AND expires_at > ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countOpenByPlayer(Connection conn, UUID creatorUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM orders WHERE creator_uuid = ? AND status = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, creatorUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<Order> findByCreatorAndStatus(Connection conn, UUID creatorUuid,
                                              OrderStatus status) throws SQLException {
        String sql = "SELECT * FROM orders WHERE creator_uuid = ? AND status = ? ORDER BY created_at DESC";
        List<Order> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, creatorUuid.toString());
            ps.setByte(2, status.getCode());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    public void updateStatus(Connection conn, UUID id, OrderStatus status, UUID fulfillerUuid,
                             String fulfillerName, Long fulfilledAt) throws SQLException {
        String sql = "UPDATE orders SET status = ?, fulfiller_uuid = ?, fulfiller_name = ?, "
                + "fulfilled_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setByte(1, status.getCode());
            if (fulfillerUuid != null) {
                ps.setString(2, fulfillerUuid.toString());
            } else {
                ps.setNull(2, Types.CHAR);
            }
            if (fulfillerName != null) {
                ps.setString(3, fulfillerName);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            if (fulfilledAt != null) {
                ps.setLong(4, fulfilledAt);
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            ps.setString(5, id.toString());
            ps.executeUpdate();
        }
    }

    public List<Order> findExpired(Connection conn) throws SQLException {
        String sql = "SELECT * FROM orders WHERE status = 0 AND expires_at <= ?";
        List<Order> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        String fulfillerStr = rs.getString("fulfiller_uuid");
        UUID fulfillerUuid = fulfillerStr != null ? UUID.fromString(fulfillerStr) : null;

        long fulfilledAtLong = rs.getLong("fulfilled_at");
        Long fulfilledAt = rs.wasNull() ? null : fulfilledAtLong;

        return Order.builder()
                .id(UUID.fromString(rs.getString("id")))
                .creatorUuid(UUID.fromString(rs.getString("creator_uuid")))
                .creatorName(rs.getString("creator_name"))
                .itemType(rs.getString("item_type"))
                .quantity(rs.getInt("quantity"))
                .totalPrice(rs.getLong("total_price"))
                .status(OrderStatus.fromCode(rs.getByte("status")))
                .createdAt(rs.getLong("created_at"))
                .expiresAt(rs.getLong("expires_at"))
                .fulfillerUuid(fulfillerUuid)
                .fulfillerName(rs.getString("fulfiller_name"))
                .fulfilledAt(fulfilledAt)
                .build();
    }
}
