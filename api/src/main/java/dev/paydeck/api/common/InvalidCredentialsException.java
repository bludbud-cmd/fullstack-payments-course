package dev.paydeck.api.common;

/** Sign-in failed. The message never says which part was wrong, so it cannot be used to find accounts. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
