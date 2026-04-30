package it.thatsade.unrealauctions.storage;

import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.model.ListingStatus;
import it.thatsade.unrealauctions.model.ListingType;
import it.thatsade.unrealauctions.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ListingRepository {

    private final DatabaseManager db;

    public ListingRepository(DatabaseManager db) {
        this.db = db;
    }

    public void insert(Connection conn, Listing listing) throws SQLException {
        String sql = """
                INSERT INTO listings
                    (id, seller_uuid, seller_name, item_data, item_type, price,
                     listing_type, status, created_at, expires_at, buyer_uuid, sold_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, listing.getId().toString());
            ps.setString(2, listing.getSellerUuid().toString());
            ps.setString(3, listing.getSellerName());
            ps.setBytes(4, listing.getItemData());
            ps.setString(5, listing.getItemType());
            ps.setLong(6, listing.getPrice());
            ps.setByte(7, listing.getListingType().getCode());
            ps.setByte(8, listing.getStatus().getCode());
            ps.setLong(9, listing.getCreatedAt());
            ps.setLong(10, listing.getExpiresAt());
            if (listing.getBuyerUuid() != null) {
                ps.setString(11, listing.getBuyerUuid().toString());
            } else {
                ps.setNull(11, Types.CHAR);
            }
            if (listing.getSoldAt() != null) {
                ps.setLong(12, listing.getSoldAt());
            } else {
                ps.setNull(12, Types.BIGINT);
            }
            ps.executeUpdate();
        }
    }

    public Optional<Listing> findById(Connection conn, UUID id) throws SQLException {
        String sql = "SELECT * FROM listings WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public List<Listing> findActiveListings(Connection conn, int offset, int limit) throws SQLException {
        String sql = """
                SELECT * FROM listings
                WHERE status = 0 AND expires_at > ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;
        List<Listing> results = new ArrayList<>();
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

    public int countActiveListings(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM listings WHERE status = 0 AND expires_at > " + System.currentTimeMillis())) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public List<Listing> findBySellerAndStatus(Connection conn, UUID sellerUuid, ListingStatus status) throws SQLException {
        String sql = "SELECT * FROM listings WHERE seller_uuid = ? AND status = ? ORDER BY created_at DESC";
        List<Listing> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerUuid.toString());
            ps.setByte(2, status.getCode());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    public int countActiveByPlayer(Connection conn, UUID sellerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM listings WHERE seller_uuid = ? AND status = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void updateStatus(Connection conn, UUID id, ListingStatus status,
                             UUID buyerUuid, Long soldAt) throws SQLException {
        String sql = "UPDATE listings SET status = ?, buyer_uuid = ?, sold_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setByte(1, status.getCode());
            if (buyerUuid != null) {
                ps.setString(2, buyerUuid.toString());
            } else {
                ps.setNull(2, Types.CHAR);
            }
            if (soldAt != null) {
                ps.setLong(3, soldAt);
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            ps.setString(4, id.toString());
            ps.executeUpdate();
        }
    }

    public List<Listing> findExpired(Connection conn) throws SQLException {
        String sql = "SELECT * FROM listings WHERE status = 0 AND expires_at <= ?";
        List<Listing> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    private Listing mapRow(ResultSet rs) throws SQLException {
        String idStr = rs.getString("id");
        byte[] itemData = rs.getBytes("item_data");
        ItemStack item = ItemSerializer.deserialize(itemData, idStr);

        String buyerStr = rs.getString("buyer_uuid");
        UUID buyerUuid = buyerStr != null ? UUID.fromString(buyerStr) : null;

        long soldAtLong = rs.getLong("sold_at");
        Long soldAt = rs.wasNull() ? null : soldAtLong;

        return Listing.builder()
                .id(UUID.fromString(idStr))
                .sellerUuid(UUID.fromString(rs.getString("seller_uuid")))
                .sellerName(rs.getString("seller_name"))
                .item(item)
                .itemData(itemData)
                .itemType(rs.getString("item_type"))
                .price(rs.getLong("price"))
                .listingType(ListingType.fromCode(rs.getByte("listing_type")))
                .status(ListingStatus.fromCode(rs.getByte("status")))
                .createdAt(rs.getLong("created_at"))
                .expiresAt(rs.getLong("expires_at"))
                .buyerUuid(buyerUuid)
                .soldAt(soldAt)
                .build();
    }
}
