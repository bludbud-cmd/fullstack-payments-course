package dev.paydeck.api.common;

/** The request clashes with data that already exists, such as an email that is already registered. */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
