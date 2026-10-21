package dev.paydeck.api.access;

/** Things someone might try to do with a card. */
public enum CardAction {
    VIEW,
    /** Pay a shop with it. */
    PAY,
    FREEZE,
    UNFREEZE,
    /** Rename it or change its spending limit. */
    CHANGE_SETTINGS,
    CANCEL
}
