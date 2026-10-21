package dev.paydeck.api.stripe;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StripeCheckoutRepository extends JpaRepository<StripeCheckout, String> {

    /** Stripe may send the same event twice at the same moment; the lock makes them take turns. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StripeCheckout c where c.id = :id")
    Optional<StripeCheckout> findByIdForUpdate(String id);
}
