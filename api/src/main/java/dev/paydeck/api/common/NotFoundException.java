package dev.paydeck.api.common;

/** Thrown when a request names something that does not exist. Becomes a 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String thing, Object id) {
        super(thing + " " + id + " was not found");
    }
}
