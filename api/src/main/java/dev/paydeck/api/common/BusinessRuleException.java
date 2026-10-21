package dev.paydeck.api.common;

/**
 * Thrown when a request is well formed but breaks a rule, such as spending
 * more than the balance. Becomes a 422 with a machine-readable {@code code}.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
