package dev.paydeck.api.ledger;

import java.time.Instant;
import java.util.UUID;

/** One line of an account's history, as the account holder sees it. */
public record AccountActivity(
        UUID transactionId, TransactionType type, String description, long amountCents, Instant createdAt) {
}
