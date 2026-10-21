package dev.paydeck.api.money;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.paydeck.api.account.Account;
import dev.paydeck.api.account.AccountKind;
import dev.paydeck.api.account.AccountRepository;
import dev.paydeck.api.common.BusinessRuleException;
import dev.paydeck.api.common.IdempotencyConflictException;
import dev.paydeck.api.common.NotFoundException;
import dev.paydeck.api.ledger.LedgerService;
import dev.paydeck.api.ledger.LedgerTransaction;
import dev.paydeck.api.ledger.Posting;
import dev.paydeck.api.ledger.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tops up and transfers money. Each method follows the same recipe:
 * lock the accounts, check the idempotency key, check the rules, then record
 * one balanced ledger transaction. All of it happens in one database
 * transaction, so it either happens completely or not at all.
 */
@Service
public class MoneyMovementService {

    private final AccountRepository accounts;
    private final LedgerService ledger;

    public MoneyMovementService(AccountRepository accounts, LedgerService ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional
    public MovementResult topUp(UUID accountId, long amountCents, String description, String idempotencyKey) {
        String fingerprint = fingerprint(TransactionType.TOP_UP, accountId, amountCents, description);
        Account account = lockCustomerAccount(accountId);

        Optional<MovementResult> replay = replay(idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        Account outsideWorld = accounts.findByKindAndCurrency(AccountKind.SYSTEM, account.getCurrency())
                .orElseThrow(() -> new IllegalStateException("No system account for " + account.getCurrency()));
        LedgerTransaction transaction = ledger.record(TransactionType.TOP_UP, description, idempotencyKey, fingerprint,
                List.of(new Posting(outsideWorld.getId(), -amountCents), new Posting(account.getId(), amountCents)));
        return new MovementResult(transaction, false);
    }

    @Transactional
    public MovementResult transfer(UUID fromId, UUID toId, long amountCents, String description,
                                   String idempotencyKey) {
        if (fromId.equals(toId)) {
            throw new BusinessRuleException("SAME_ACCOUNT", "You cannot transfer money to the same account");
        }
        String fingerprint = fingerprint(TransactionType.TRANSFER, fromId, toId, amountCents, description);

        // Always lock in the same order. If two transfers between A and B run at
        // once, in opposite directions, neither can end up waiting for the other forever.
        boolean fromFirst = fromId.compareTo(toId) < 0;
        Account first = lockCustomerAccount(fromFirst ? fromId : toId);
        Account second = lockCustomerAccount(fromFirst ? toId : fromId);
        Account from = fromFirst ? first : second;
        Account to = fromFirst ? second : first;

        Optional<MovementResult> replay = replay(idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        if (from.getCurrency() != to.getCurrency()) {
            throw new BusinessRuleException("CURRENCY_MISMATCH",
                    "Both accounts must use the same currency (" + from.getCurrency() + " and " + to.getCurrency() + ")");
        }
        if (ledger.balanceOf(from.getId()) < amountCents) {
            throw new BusinessRuleException("INSUFFICIENT_FUNDS", "There is not enough money in the account");
        }

        LedgerTransaction transaction = ledger.record(TransactionType.TRANSFER, description, idempotencyKey,
                fingerprint, List.of(new Posting(from.getId(), -amountCents), new Posting(to.getId(), amountCents)));
        return new MovementResult(transaction, false);
    }

    private Account lockCustomerAccount(UUID id) {
        return accounts.findByIdForUpdate(id)
                .filter(account -> account.getKind() == AccountKind.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Account", id));
    }

    /** If this key was used before, returns the earlier result, or refuses if the request was different. */
    private Optional<MovementResult> replay(String idempotencyKey, String fingerprint) {
        return ledger.findByIdempotencyKey(idempotencyKey).map(earlier -> {
            if (!earlier.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return new MovementResult(earlier, true);
        });
    }

    /** A short summary of the request, used to spot a key being reused for a different request. */
    static String fingerprint(Object... parts) {
        StringBuilder text = new StringBuilder();
        for (Object part : parts) {
            text.append(part).append('\u001f');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
