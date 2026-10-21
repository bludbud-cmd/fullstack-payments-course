package dev.paydeck.api.access;

/** Things someone might try to do with an account. */
public enum AccountAction {
    /** See the balance, the activity and the cards. */
    VIEW,
    /** Top up or send money from it. */
    MOVE_MONEY,
    /** Issue a new card that spends from it. */
    ISSUE_CARD
}
