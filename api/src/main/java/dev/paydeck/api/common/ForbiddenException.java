package dev.paydeck.api.common;

/** The person can see this thing but is not allowed to do this to it. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
