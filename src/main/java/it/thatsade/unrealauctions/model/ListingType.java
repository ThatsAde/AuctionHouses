package it.thatsade.unrealauctions.model;

public enum ListingType {
    BUY_NOW(0);

    private final byte code;

    ListingType(int code) {
        this.code = (byte) code;
    }

    public byte getCode() {
        return code;
    }

    public static ListingType fromCode(byte code) {
        for (ListingType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("Unknown ListingType code: " + code);
    }
}
