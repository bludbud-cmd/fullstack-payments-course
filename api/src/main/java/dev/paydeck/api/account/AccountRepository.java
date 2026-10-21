package dev.paydeck.api.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.paydeck.api.money.SupportedCurrency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Loads an account and locks its row until the current database transaction
     * ends ({@code SELECT ... FOR UPDATE}). Two payments from the same account
     * then run one after the other instead of both spending the same money.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(UUID id);

    Optional<Account> findByKindAndCurrency(AccountKind kind, SupportedCurrency currency);

    List<Account> findByOwnerUserIdOrderByCreatedAt(UUID ownerUserId);
}
