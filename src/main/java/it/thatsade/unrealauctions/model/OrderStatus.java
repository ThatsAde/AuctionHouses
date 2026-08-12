package it.thatsade.unrealauctions.model;

public enum OrderStatus {
    OPEN(0),
    FULFILLED(1),
    CANCELLED(2),
    EXPIRED(3);

    private final byte code;

    OrderStatus(int code) {
        this.code = (byte) code;
    }

    public byte getCode() {
        return code;
    }

    public static OrderStatus fromCode(byte code) {
        for (OrderStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("Unknown OrderStatus code: " + code);
    }
}
