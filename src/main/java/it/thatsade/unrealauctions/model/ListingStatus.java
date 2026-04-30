package it.thatsade.unrealauctions.model;

public enum ListingStatus {
    ACTIVE(0),
    SOLD(1),
    CANCELLED(2),
    EXPIRED(3);

    private final byte code;

    ListingStatus(int code) {
        this.code = (byte) code;
    }

    public byte getCode() {
        return code;
    }

    public static ListingStatus fromCode(byte code) {
        for (ListingStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("Unknown ListingStatus code: " + code);
    }
}
