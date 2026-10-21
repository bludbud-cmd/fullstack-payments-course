package dev.paydeck.api.common;

/** Thrown when an Idempotency-Key is reused for a different request. Becomes a 409. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key " + key + " was already used for a different request");
    }
}
