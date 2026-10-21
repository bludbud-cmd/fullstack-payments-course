package dev.paydeck.api.payments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.paydeck.api.access.AccessPolicy;
import dev.paydeck.api.access.CardAction;
import dev.paydeck.api.audit.AuditLog;
import dev.paydeck.api.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paying with a card. In real life a shop's card machine would ask; here the
 * app's "pay a shop" screen plays the shop, signed in as the cardholder.
 */
@RestController
public class CardPaymentController {

    public record PayRequest(
            @Positive @Max(10_000_000) long amountCents,
            @NotBlank @Size(max = 100) String merchant) {
    }

    public record CardPaymentResponse(UUID id, UUID cardId, String merchant, long amountCents,
                                      CardPaymentStatus status, DeclineReason declineReason, String declineMessage,
                                      Instant createdAt) {

        static CardPaymentResponse of(CardPayment payment) {
            DeclineReason reason = payment.getDeclineReason();
            return new CardPaymentResponse(payment.getId(), payment.getCardId(), payment.getMerchant(),
                    payment.getAmountCents(), payment.getStatus(), reason, reason == null ? null : reason.message(),
                    payment.getCreatedAt());
        }
    }

    private final CardPaymentService service;
    private final CardPaymentRepository payments;
    private final AccessPolicy access;
    private final AuditLog audit;

    public CardPaymentController(CardPaymentService service, CardPaymentRepository payments, AccessPolicy access,
                                 AuditLog audit) {
        this.service = service;
        this.payments = payments;
        this.access = access;
        this.audit = audit;
    }

    /**
     * A decline is still a successful answer to "may I pay?", so both come back
     * as 201 Created; look at {@code status}.
     */
    @PostMapping("/api/cards/{cardId}/payments")
    public ResponseEntity<CardPaymentResponse> pay(
            CurrentUser user,
            @PathVariable UUID cardId,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody PayRequest request) {
        access.card(user, cardId, CardAction.PAY);
        CardPayment payment = service.pay(cardId, request.amountCents(), request.merchant().strip(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(CardPaymentResponse.of(payment));
    }

    @GetMapping("/api/cards/{cardId}/payments")
    @Transactional(readOnly = true)
    public List<CardPaymentResponse> list(CurrentUser user, @PathVariable UUID cardId) {
        access.card(user, cardId, CardAction.VIEW);
        return payments.findTop50ByCardIdOrderByCreatedAtDesc(cardId).stream().map(CardPaymentResponse::of).toList();
    }

    /** Disputes are handled by PayDeck staff, never by the person who paid. */
    @PostMapping("/api/admin/card-payments/{id}/refund")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public CardPaymentResponse refund(CurrentUser staff, @PathVariable UUID id) {
        CardPayment refunded = service.refund(id);
        audit.recordNow(staff.id(), "CARD_PAYMENT_REFUNDED", "CARD_PAYMENT", id, "by " + staff.role());
        return CardPaymentResponse.of(refunded);
    }
}
