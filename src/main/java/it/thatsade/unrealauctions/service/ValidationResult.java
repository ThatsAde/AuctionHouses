package it.thatsade.unrealauctions.service;

public record ValidationResult(boolean valid, String messageKey) {

    public static ValidationResult ok() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult fail(String messageKey) {
        return new ValidationResult(false, messageKey);
    }

    public boolean isInvalid() {
        return !valid;
    }
}
