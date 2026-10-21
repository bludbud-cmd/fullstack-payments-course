package dev.paydeck.api.auth;

/**
 * What a person may do across the whole of PayDeck. Who owns or holds a
 * particular account or card is decided separately, in
 * {@link dev.paydeck.api.access.AccessPolicy}.
 */
public enum Role {
    /** Someone using PayDeck for their own money. */
    CUSTOMER,
    /** Staff who help customers: they can look at any account and freeze cards, but not move money. */
    SUPPORT,
    /** Staff who run PayDeck: everything support can do, plus changing roles and reading the audit log. */
    ADMIN
}
