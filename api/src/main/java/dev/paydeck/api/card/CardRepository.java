package dev.paydeck.api.card;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByAccountIdOrderByCreatedAt(UUID accountId);

    List<Card> findByCardholderUserIdOrderByCreatedAt(UUID cardholderUserId);
}
