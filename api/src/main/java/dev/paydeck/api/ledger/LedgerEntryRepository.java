package dev.paydeck.api.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query("select coalesce(sum(e.amountCents), 0) from LedgerEntry e where e.accountId = :accountId")
    long sumByAccountId(UUID accountId);

    @Query("""
            select new dev.paydeck.api.ledger.AccountActivity(t.id, t.type, t.description, e.amountCents, t.createdAt)
            from LedgerEntry e join e.transaction t
            where e.accountId = :accountId
            order by t.createdAt desc, t.id
            """)
    List<AccountActivity> findActivity(UUID accountId, Pageable page);
}
