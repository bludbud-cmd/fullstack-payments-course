package dev.paydeck.api.ledger;

public enum TransactionType {
    /** Money comes in from outside PayDeck, for example from a card. */
    TOP_UP,
    /** Money moves from one PayDeck account to another. */
    TRANSFER,
    /** Someone paid a shop with a PayDeck card. */
    CARD_PAYMENT,
    /** A card payment was given back. */
    REFUND
}
