package it.thatsade.unrealauctions.model;

import org.bukkit.Material;

import java.util.UUID;

public final class Order {

    private final UUID id;
    private final UUID creatorUuid;
    private final String creatorName;
    private final String itemType;
    private final int quantity;
    private final long totalPrice;
    private final OrderStatus status;
    private final long createdAt;
    private final long expiresAt;
    private final UUID fulfillerUuid;
    private final String fulfillerName;
    private final Long fulfilledAt;

    private Order(Builder b) {
        this.id = b.id;
        this.creatorUuid = b.creatorUuid;
        this.creatorName = b.creatorName;
        this.itemType = b.itemType;
        this.quantity = b.quantity;
        this.totalPrice = b.totalPrice;
        this.status = b.status;
        this.createdAt = b.createdAt;
        this.expiresAt = b.expiresAt;
        this.fulfillerUuid = b.fulfillerUuid;
        this.fulfillerName = b.fulfillerName;
        this.fulfilledAt = b.fulfilledAt;
    }

    public UUID getId() { return id; }
    public UUID getCreatorUuid() { return creatorUuid; }
    public String getCreatorName() { return creatorName; }
    public String getItemType() { return itemType; }
    public int getQuantity() { return quantity; }
    public long getTotalPrice() { return totalPrice; }
    public OrderStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public UUID getFulfillerUuid() { return fulfillerUuid; }
    public String getFulfillerName() { return fulfillerName; }
    public Long getFulfilledAt() { return fulfilledAt; }

    /** Resolves the requested material, or null if the name is no longer valid. */
    public Material getMaterial() {
        return Material.matchMaterial(itemType);
    }

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
        private UUID creatorUuid;
        private String creatorName;
        private String itemType;
        private int quantity = 1;
        private long totalPrice;
        private OrderStatus status = OrderStatus.OPEN;
        private long createdAt;
        private long expiresAt;
        private UUID fulfillerUuid;
        private String fulfillerName;
        private Long fulfilledAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder creatorUuid(UUID creatorUuid) { this.creatorUuid = creatorUuid; return this; }
        public Builder creatorName(String creatorName) { this.creatorName = creatorName; return this; }
        public Builder itemType(String itemType) { this.itemType = itemType; return this; }
        public Builder quantity(int quantity) { this.quantity = quantity; return this; }
        public Builder totalPrice(long totalPrice) { this.totalPrice = totalPrice; return this; }
        public Builder status(OrderStatus status) { this.status = status; return this; }
        public Builder createdAt(long createdAt) { this.createdAt = createdAt; return this; }
        public Builder expiresAt(long expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder fulfillerUuid(UUID fulfillerUuid) { this.fulfillerUuid = fulfillerUuid; return this; }
        public Builder fulfillerName(String fulfillerName) { this.fulfillerName = fulfillerName; return this; }
        public Builder fulfilledAt(Long fulfilledAt) { this.fulfilledAt = fulfilledAt; return this; }

        public Order build() {
            return new Order(this);
        }
    }
}
