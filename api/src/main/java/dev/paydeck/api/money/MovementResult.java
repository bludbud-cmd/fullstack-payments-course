package dev.paydeck.api.money;

import dev.paydeck.api.ledger.LedgerTransaction;

/** A recorded movement of money, and whether it was recorded just now or by an earlier identical request. */
public record MovementResult(LedgerTransaction transaction, boolean replayed) {
}
