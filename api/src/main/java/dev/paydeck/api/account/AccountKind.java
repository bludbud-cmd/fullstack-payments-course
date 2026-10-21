package dev.paydeck.api.account;

public enum AccountKind {
    /** Belongs to a PayDeck user. Its balance can never go below zero. */
    CUSTOMER,
    /** Stands for the outside world, so money coming in has somewhere to come from. */
    SYSTEM
}
