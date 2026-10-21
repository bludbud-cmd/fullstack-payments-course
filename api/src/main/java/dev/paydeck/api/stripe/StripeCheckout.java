package dev.paydeck.api.stripe;

import java.time.Instant;
import java.util.UUID;

import dev.paydeck.api.money.SupportedCurrency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A Stripe payment page opened for a top-up, and whether it has been paid. */
@Entity
@Table(name = "stripe_checkouts")
public class StripeCheckout {

    public enum Status {
        OPEN,
        PAID,
        EXPIRED
    }

    /** Stripe's ID for the Checkout Session. */
    @Id
    private String id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportedCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    protected StripeCheckout() {
        // For JPA
    }

    public StripeCheckout(String id, UUID accountId, long amountCents, SupportedCurrency currency) {
        this.id = id;
        this.accountId = accountId;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = Status.OPEN;
        this.createdAt = Instant.now();
    }

    void markPaid() {
        status = Status.PAID;
        completedAt = Instant.now();
    }

    void markExpired() {
        status = Status.EXPIRED;
        completedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public SupportedCurrency getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }
}
