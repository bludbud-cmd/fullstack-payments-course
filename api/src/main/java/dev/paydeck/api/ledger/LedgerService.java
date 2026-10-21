package dev.paydeck.api.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way money moves in PayDeck. Every transaction must balance:
 * whatever leaves one account arrives in another, so money is never created
 * or lost by accident.
 */
@Service
public class LedgerService {

    private static final int HISTORY_LIMIT = 100;

    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;

    public LedgerService(LedgerTransactionRepository transactions, LedgerEntryRepository entries) {
        this.transactions = transactions;
        this.entries = entries;
    }

    /** Must run inside the caller's transaction, after it has locked the accounts it debits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction record(TransactionType type, String description, String idempotencyKey,
                                    String requestFingerprint, List<Posting> postings) {
        checkBalanced(postings);
        LedgerTransaction transaction = new LedgerTransaction(type, description, idempotencyKey, requestFingerprint);
        postings.forEach(posting -> transaction.addEntry(posting.accountId(), posting.amountCents()));
        return transactions.save(transaction);
    }

    static void checkBalanced(List<Posting> postings) {
        if (postings.size() < 2) {
            throw new IllegalArgumentException("A transaction needs at least two postings");
        }
        if (postings.stream().anyMatch(posting -> posting.amountCents() == 0)) {
            throw new IllegalArgumentException("A posting cannot be zero");
        }
        long total = postings.stream().mapToLong(Posting::amountCents).reduce(0, Math::addExact);
        if (total != 0) {
            throw new IllegalArgumentException("Postings must add up to zero but add up to " + total);
        }
    }

    @Transactional(readOnly = true)
    public Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey) {
        return transactions.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public long balanceOf(UUID accountId) {
        return entries.sumByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<AccountActivity> activityOf(UUID accountId) {
        return entries.findActivity(accountId, PageRequest.of(0, HISTORY_LIMIT));
    }
}
