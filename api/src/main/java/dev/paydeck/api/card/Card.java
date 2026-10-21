package dev.paydeck.api.card;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import dev.paydeck.api.common.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A virtual card that spends from one account. We only keep the last four
 * digits; a full card number never reaches PayDeck's database.
 */
@Entity
@Table(name = "cards")
public class Card {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String last4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;

    /** The most one payment can be, or {@code null} for no limit. */
    private Long spendingLimitCents;

    @Column(nullable = false)
    private Instant createdAt;

    protected Card() {
        // For JPA
    }

    public static Card issue(UUID accountId, String nickname, Long spendingLimitCents) {
        Card card = new Card();
        card.accountId = accountId;
        card.nickname = nickname;
        card.last4 = String.format("%04d", RANDOM.nextInt(10_000));
        card.status = CardStatus.ACTIVE;
        card.spendingLimitCents = spendingLimitCents;
        card.createdAt = Instant.now();
        return card;
    }

    public void rename(String nickname) {
        requireNotCancelled();
        this.nickname = nickname;
    }

    public void setSpendingLimit(Long spendingLimitCents) {
        requireNotCancelled();
        this.spendingLimitCents = spendingLimitCents;
    }

    public void freeze() {
        if (status != CardStatus.ACTIVE) {
            throw new BusinessRuleException("CARD_NOT_ACTIVE", "Only an active card can be frozen");
        }
        status = CardStatus.FROZEN;
    }

    public void unfreeze() {
        if (status != CardStatus.FROZEN) {
            throw new BusinessRuleException("CARD_NOT_FROZEN", "Only a frozen card can be unfrozen");
        }
        status = CardStatus.ACTIVE;
    }

    public void cancel() {
        requireNotCancelled();
        status = CardStatus.CANCELLED;
    }

    private void requireNotCancelled() {
        if (status == CardStatus.CANCELLED) {
            throw new BusinessRuleException("CARD_CANCELLED", "A cancelled card cannot be changed");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getLast4() {
        return last4;
    }

    public CardStatus getStatus() {
        return status;
    }

    public Long getSpendingLimitCents() {
        return spendingLimitCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
