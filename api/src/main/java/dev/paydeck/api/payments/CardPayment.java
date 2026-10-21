package dev.paydeck.api.payments;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One attempt to pay a shop with a PayDeck card. */
@Entity
@Table(name = "card_payments")
public class CardPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID cardId;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String merchant;

    @Column(nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardPaymentStatus status;

    @Enumerated(EnumType.STRING)
    private DeclineReason declineReason;

    /** The ledger transaction that moved the money; empty for a decline. */
    private UUID transactionId;

    private UUID refundTransactionId;

    @Column(nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestFingerprint;

    @Column(nullable = false)
    private Instant createdAt;

    protected CardPayment() {
        // For JPA
    }

    private CardPayment(UUID cardId, UUID accountId, String merchant, long amountCents, String idempotencyKey,
                        String requestFingerprint) {
        this.cardId = cardId;
        this.accountId = accountId;
        this.merchant = merchant;
        this.amountCents = amountCents;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = Instant.now();
    }

    static CardPayment approved(UUID cardId, UUID accountId, String merchant, long amountCents, UUID transactionId,
                                String idempotencyKey, String fingerprint) {
        CardPayment payment = new CardPayment(cardId, accountId, merchant, amountCents, idempotencyKey, fingerprint);
        payment.status = CardPaymentStatus.APPROVED;
        payment.transactionId = transactionId;
        return payment;
    }

    static CardPayment declined(UUID cardId, UUID accountId, String merchant, long amountCents, DeclineReason reason,
                                String idempotencyKey, String fingerprint) {
        CardPayment payment = new CardPayment(cardId, accountId, merchant, amountCents, idempotencyKey, fingerprint);
        payment.status = CardPaymentStatus.DECLINED;
        payment.declineReason = reason;
        return payment;
    }

    void refunded(UUID refundTransactionId) {
        this.status = CardPaymentStatus.REFUNDED;
        this.refundTransactionId = refundTransactionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getMerchant() {
        return merchant;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public CardPaymentStatus getStatus() {
        return status;
    }

    public DeclineReason getDeclineReason() {
        return declineReason;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
