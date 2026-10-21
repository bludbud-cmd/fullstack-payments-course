package dev.paydeck.api.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CardPaymentRepository extends JpaRepository<CardPayment, UUID> {

    Optional<CardPayment> findByIdempotencyKey(String idempotencyKey);

    List<CardPayment> findTop50ByCardIdOrderByCreatedAtDesc(UUID cardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CardPayment p where p.id = :id")
    Optional<CardPayment> findByIdForUpdate(UUID id);
}
