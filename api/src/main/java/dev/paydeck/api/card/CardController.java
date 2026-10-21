package dev.paydeck.api.card;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.paydeck.api.access.AccessPolicy;
import dev.paydeck.api.access.AccountAction;
import dev.paydeck.api.access.CardAction;
import dev.paydeck.api.auth.CurrentUser;
import dev.paydeck.api.auth.User;
import dev.paydeck.api.auth.UserRepository;
import dev.paydeck.api.common.BusinessRuleException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CardController {

    /** Leave out {@code cardholderEmail} to keep the card yourself, or give the email of another PayDeck user. */
    public record IssueCardRequest(
            @NotBlank @Size(max = 50) String nickname,
            @Positive Long spendingLimitCents,
            @Email String cardholderEmail) {
    }

    /**
     * Only the fields that are sent change. To remove a spending limit, send
     * {@code "removeSpendingLimit": true}.
     */
    public record UpdateCardRequest(
            @Size(min = 1, max = 50) String nickname,
            @Positive Long spendingLimitCents,
            Boolean removeSpendingLimit) {
    }

    public record CardResponse(
            UUID id, UUID accountId, String nickname, String last4, CardStatus status,
            Long spendingLimitCents, UUID cardholderId, Instant createdAt) {

        public static CardResponse of(Card card) {
            return new CardResponse(card.getId(), card.getAccountId(), card.getNickname(), card.getLast4(),
                    card.getStatus(), card.getSpendingLimitCents(), card.getCardholderUserId(),
                    card.getCreatedAt());
        }
    }

    private final CardRepository cards;
    private final UserRepository users;
    private final AccessPolicy access;

    public CardController(CardRepository cards, UserRepository users, AccessPolicy access) {
        this.cards = cards;
        this.users = users;
        this.access = access;
    }

    @PostMapping("/api/accounts/{accountId}/cards")
    @Transactional
    public ResponseEntity<CardResponse> issue(CurrentUser user, @PathVariable UUID accountId,
                                              @Valid @RequestBody IssueCardRequest request) {
        access.account(user, accountId, AccountAction.ISSUE_CARD);
        UUID cardholder = null;
        if (request.cardholderEmail() != null && !request.cardholderEmail().isBlank()) {
            UUID found = users.findByEmail(User.normalizeEmail(request.cardholderEmail()))
                    .map(User::getId)
                    .orElseThrow(() -> new BusinessRuleException("CARDHOLDER_NOT_FOUND",
                            "Nobody with that email uses PayDeck yet. Ask them to sign up first."));
            cardholder = found.equals(user.id()) ? null : found;
        }
        Card card = cards.save(Card.issue(accountId, request.nickname().strip(), request.spendingLimitCents(),
                cardholder));
        return ResponseEntity.created(URI.create("/api/cards/" + card.getId())).body(CardResponse.of(card));
    }

    @GetMapping("/api/accounts/{accountId}/cards")
    @Transactional(readOnly = true)
    public List<CardResponse> list(CurrentUser user, @PathVariable UUID accountId) {
        access.account(user, accountId, AccountAction.VIEW);
        return cards.findByAccountIdOrderByCreatedAt(accountId).stream().map(CardResponse::of).toList();
    }

    @GetMapping("/api/cards/{id}")
    @Transactional(readOnly = true)
    public CardResponse get(CurrentUser user, @PathVariable UUID id) {
        return CardResponse.of(access.card(user, id, CardAction.VIEW));
    }

    @PatchMapping("/api/cards/{id}")
    @Transactional
    public CardResponse update(CurrentUser user, @PathVariable UUID id,
                               @Valid @RequestBody UpdateCardRequest request) {
        Card card = access.card(user, id, CardAction.CHANGE_SETTINGS);
        if (request.nickname() != null) {
            card.rename(request.nickname().strip());
        }
        if (Boolean.TRUE.equals(request.removeSpendingLimit())) {
            card.setSpendingLimit(null);
        } else if (request.spendingLimitCents() != null) {
            card.setSpendingLimit(request.spendingLimitCents());
        }
        return CardResponse.of(card);
    }

    @PostMapping("/api/cards/{id}/freeze")
    @Transactional
    public CardResponse freeze(CurrentUser user, @PathVariable UUID id) {
        Card card = access.card(user, id, CardAction.FREEZE);
        card.freeze();
        return CardResponse.of(card);
    }

    @PostMapping("/api/cards/{id}/unfreeze")
    @Transactional
    public CardResponse unfreeze(CurrentUser user, @PathVariable UUID id) {
        Card card = access.card(user, id, CardAction.UNFREEZE);
        card.unfreeze();
        return CardResponse.of(card);
    }

    @DeleteMapping("/api/cards/{id}")
    @Transactional
    public ResponseEntity<Void> cancel(CurrentUser user, @PathVariable UUID id) {
        access.card(user, id, CardAction.CANCEL).cancel();
        return ResponseEntity.noContent().build();
    }
}
