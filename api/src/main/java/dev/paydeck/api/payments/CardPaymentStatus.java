package dev.paydeck.api.payments;

public enum CardPaymentStatus {
    APPROVED,
    DECLINED,
    /** Approved, then given back. */
    REFUNDED
}
