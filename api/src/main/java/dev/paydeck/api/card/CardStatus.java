package dev.paydeck.api.card;

public enum CardStatus {
    /** Can be used to pay. */
    ACTIVE,
    /** Temporarily blocked by its owner. Can be unfrozen. */
    FROZEN,
    /** Permanently closed. Cannot be changed again. */
    CANCELLED
}
