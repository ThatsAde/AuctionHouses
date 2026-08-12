package it.thatsade.unrealauctions.model;

public enum CollectionSource {
    PURCHASE(0),
    CANCELLATION(1),
    EXPIRY(2),
    ORDER_FULFILLED(3);

    private final byte code;

    CollectionSource(int code) {
        this.code = (byte) code;
    }

    public byte getCode() {
        return code;
    }

    public static CollectionSource fromCode(byte code) {
        for (CollectionSource s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("Unknown CollectionSource code: " + code);
    }
}
