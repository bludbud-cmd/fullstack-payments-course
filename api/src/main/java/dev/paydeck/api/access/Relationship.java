package dev.paydeck.api.access;

/** How the person asking is connected to an account or card. */
public enum Relationship {
    /** Owns the account (and so every card on it). */
    OWNER,
    /** Was given this card by the account's owner. */
    CARDHOLDER,
    /** Not connected at all. Only staff can get anywhere from here. */
    NONE
}
