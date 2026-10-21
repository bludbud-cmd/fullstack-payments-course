package dev.paydeck.api.ledger;

import java.util.UUID;

/** A request to add {@code amountCents} (negative to take money away) to one account. */
public record Posting(UUID accountId, long amountCents) {
}
