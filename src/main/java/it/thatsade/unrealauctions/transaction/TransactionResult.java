package it.thatsade.unrealauctions.transaction;

public final class TransactionResult {

    public enum Status { SUCCESS, FAILURE }

    private final Status status;
    private final String messageKey;
    private final Object payload;
    private final boolean connectionFailure;

    private TransactionResult(Status status, String messageKey, Object payload,
                              boolean connectionFailure) {
        this.status = status;
        this.messageKey = messageKey;
        this.payload = payload;
        this.connectionFailure = connectionFailure;
    }

    public static TransactionResult success() {
        return new TransactionResult(Status.SUCCESS, null, null, false);
    }

    public static TransactionResult success(Object payload) {
        return new TransactionResult(Status.SUCCESS, null, payload, false);
    }

    public static TransactionResult failure(String messageKey) {
        return new TransactionResult(Status.FAILURE, messageKey, null, false);
    }

    /** Failure caused by the database being unreachable (could not acquire a connection). */
    public static TransactionResult connectionFailure() {
        return new TransactionResult(Status.FAILURE, "db-unavailable", null, true);
    }

    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isFailure() { return status == Status.FAILURE; }
    public boolean isConnectionFailure() { return connectionFailure; }
    public String getMessageKey() { return messageKey; }

    @SuppressWarnings("unchecked")
    public <T> T getPayload(Class<T> type) {
        return type.cast(payload);
    }
}
