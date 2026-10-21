package dev.paydeck.api.ledger;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One line of a ledger transaction: money into (positive) or out of (negative) one account. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private LedgerTransaction transaction;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private long amountCents;

    protected LedgerEntry() {
        // For JPA
    }

    LedgerEntry(LedgerTransaction transaction, UUID accountId, long amountCents) {
        this.transaction = transaction;
        this.accountId = accountId;
        this.amountCents = amountCents;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getAmountCents() {
        return amountCents;
    }
}
