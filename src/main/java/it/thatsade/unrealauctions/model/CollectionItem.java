package it.thatsade.unrealauctions.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class CollectionItem {

    private final UUID id;
    private final UUID ownerUuid;
    private final ItemStack item;
    private final byte[] itemData;
    private final CollectionSource source;
    private final UUID sourceListingId;
    private final long addedAt;
    private final long expiresAt;

    private CollectionItem(Builder b) {
        this.id = b.id;
        this.ownerUuid = b.ownerUuid;
        this.item = b.item;
        this.itemData = b.itemData;
        this.source = b.source;
        this.sourceListingId = b.sourceListingId;
        this.addedAt = b.addedAt;
        this.expiresAt = b.expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public ItemStack getItem() { return item; }
    public byte[] getItemData() { return itemData; }
    public CollectionSource getSource() { return source; }
    public UUID getSourceListingId() { return sourceListingId; }
    public long getAddedAt() { return addedAt; }
    public long getExpiresAt() { return expiresAt; }

    public long getMillisUntilExpiry() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private UUID ownerUuid;
        private ItemStack item;
        private byte[] itemData;
        private CollectionSource source;
        private UUID sourceListingId;
        private long addedAt;
        private long expiresAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder ownerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; return this; }
        public Builder item(ItemStack item) { this.item = item; return this; }
        public Builder itemData(byte[] itemData) { this.itemData = itemData; return this; }
        public Builder source(CollectionSource source) { this.source = source; return this; }
        public Builder sourceListingId(UUID sourceListingId) { this.sourceListingId = sourceListingId; return this; }
        public Builder addedAt(long addedAt) { this.addedAt = addedAt; return this; }
        public Builder expiresAt(long expiresAt) { this.expiresAt = expiresAt; return this; }

        public CollectionItem build() {
            return new CollectionItem(this);
        }
    }
}
