package it.thatsade.unrealauctions.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class Listing {

    private final UUID id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final byte[] itemData;
    private final String itemType;
    private final long price;
    private final ListingType listingType;
    private final ListingStatus status;
    private final long createdAt;
    private final long expiresAt;
    private final UUID buyerUuid;
    private final Long soldAt;

    private Listing(Builder b) {
        this.id = b.id;
        this.sellerUuid = b.sellerUuid;
        this.sellerName = b.sellerName;
        this.item = b.item;
        this.itemData = b.itemData;
        this.itemType = b.itemType;
        this.price = b.price;
        this.listingType = b.listingType;
        this.status = b.status;
        this.createdAt = b.createdAt;
        this.expiresAt = b.expiresAt;
        this.buyerUuid = b.buyerUuid;
        this.soldAt = b.soldAt;
    }

    public UUID getId() { return id; }
    public UUID getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItem() { return item; }
    public byte[] getItemData() { return itemData; }
    public String getItemType() { return itemType; }
    public long getPrice() { return price; }
    public ListingType getListingType() { return listingType; }
    public ListingStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public UUID getBuyerUuid() { return buyerUuid; }
    public Long getSoldAt() { return soldAt; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public long getMillisRemaining() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private UUID sellerUuid;
        private String sellerName;
        private ItemStack item;
        private byte[] itemData;
        private String itemType;
        private long price;
        private ListingType listingType = ListingType.BUY_NOW;
        private ListingStatus status = ListingStatus.ACTIVE;
        private long createdAt;
        private long expiresAt;
        private UUID buyerUuid;
        private Long soldAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder sellerUuid(UUID sellerUuid) { this.sellerUuid = sellerUuid; return this; }
        public Builder sellerName(String sellerName) { this.sellerName = sellerName; return this; }
        public Builder item(ItemStack item) { this.item = item; return this; }
        public Builder itemData(byte[] itemData) { this.itemData = itemData; return this; }
        public Builder itemType(String itemType) { this.itemType = itemType; return this; }
        public Builder price(long price) { this.price = price; return this; }
        public Builder listingType(ListingType listingType) { this.listingType = listingType; return this; }
        public Builder status(ListingStatus status) { this.status = status; return this; }
        public Builder createdAt(long createdAt) { this.createdAt = createdAt; return this; }
        public Builder expiresAt(long expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder buyerUuid(UUID buyerUuid) { this.buyerUuid = buyerUuid; return this; }
        public Builder soldAt(Long soldAt) { this.soldAt = soldAt; return this; }

        public Listing build() {
            return new Listing(this);
        }
    }
}
