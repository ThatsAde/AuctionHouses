package it.thatsade.unrealauctions.storage;

import it.thatsade.unrealauctions.model.CollectionItem;
import it.thatsade.unrealauctions.model.CollectionSource;
import it.thatsade.unrealauctions.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CollectionRepository {

    private final DatabaseManager db;

    public CollectionRepository(DatabaseManager db) {
        this.db = db;
    }

    public void insert(Connection conn, CollectionItem item) throws SQLException {
        String sql = """
                INSERT INTO collection_box (id, owner_uuid, item_data, source, source_id, added_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getId().toString());
            ps.setString(2, item.getOwnerUuid().toString());
            ps.setBytes(3, item.getItemData());
            ps.setByte(4, item.getSource().getCode());
            if (item.getSourceListingId() != null) {
                ps.setString(5, item.getSourceListingId().toString());
            } else {
                ps.setNull(5, Types.CHAR);
            }
            ps.setLong(6, item.getAddedAt());
            if (item.getExpiresAt() > 0) {
                ps.setLong(7, item.getExpiresAt());
            } else {
                ps.setNull(7, Types.BIGINT);
            }
            ps.executeUpdate();
        }
    }

    /** Deletes all items whose expires_at has passed. Returns number of rows deleted. */
    public int deleteExpired(Connection conn) throws SQLException {
        String sql = "DELETE FROM collection_box WHERE expires_at IS NOT NULL AND expires_at <= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            return ps.executeUpdate();
        }
    }

    public List<CollectionItem> findByOwner(Connection conn, UUID ownerUuid) throws SQLException {
        String sql = "SELECT * FROM collection_box WHERE owner_uuid = ? ORDER BY added_at DESC";
        List<CollectionItem> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    public List<CollectionItem> findByOwnerAndSource(Connection conn, UUID ownerUuid, CollectionSource source) throws SQLException {
        String sql = "SELECT * FROM collection_box WHERE owner_uuid = ? AND source = ? ORDER BY added_at DESC";
        List<CollectionItem> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            ps.setByte(2, source.getCode());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    public boolean deleteById(Connection conn, UUID id, UUID ownerUuid) throws SQLException {
        String sql = "DELETE FROM collection_box WHERE id = ? AND owner_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.setString(2, ownerUuid.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public int countByOwner(Connection conn, UUID ownerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM collection_box WHERE owner_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private CollectionItem mapRow(ResultSet rs) throws SQLException {
        String idStr = rs.getString("id");
        byte[] itemData = rs.getBytes("item_data");
        ItemStack item = ItemSerializer.deserialize(itemData, "collection:" + idStr);

        String srcIdStr = rs.getString("source_id");
        UUID sourceListingId = srcIdStr != null ? UUID.fromString(srcIdStr) : null;

        long expiresAt = rs.getLong("expires_at");
        if (rs.wasNull()) expiresAt = 0;

        return CollectionItem.builder()
                .id(UUID.fromString(idStr))
                .ownerUuid(UUID.fromString(rs.getString("owner_uuid")))
                .item(item)
                .itemData(itemData)
                .source(CollectionSource.fromCode(rs.getByte("source")))
                .sourceListingId(sourceListingId)
                .addedAt(rs.getLong("added_at"))
                .expiresAt(expiresAt)
                .build();
    }
}
