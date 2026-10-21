package dev.paydeck.api.account;

import java.time.Instant;
import java.util.UUID;

import dev.paydeck.api.money.SupportedCurrency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An account that holds money in one currency. Its balance lives in the ledger. */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The user who owns this account. {@code null} for SYSTEM accounts. */
    private UUID ownerUserId;

    @Column(nullable = false)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportedCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountKind kind;

    @Column(nullable = false)
    private Instant createdAt;

    protected Account() {
        // For JPA
    }

    public static Account openCustomerAccount(UUID ownerUserId, String ownerName, SupportedCurrency currency) {
        Account account = new Account();
        account.ownerUserId = ownerUserId;
        account.ownerName = ownerName;
        account.currency = currency;
        account.kind = AccountKind.CUSTOMER;
        account.createdAt = Instant.now();
        return account;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public SupportedCurrency getCurrency() {
        return currency;
    }

    public AccountKind getKind() {
        return kind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
