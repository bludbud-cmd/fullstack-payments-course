package dev.paydeck.api.card;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.paydeck.api.account.AccountKind;
import dev.paydeck.api.account.AccountRepository;
import dev.paydeck.api.common.NotFoundException;
import jakarta.validation.Valid;
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

    public record IssueCardRequest(
            @NotBlank @Size(max = 50) String nickname,
            @Positive Long spendingLimitCents) {
    }

    /**
     * Only the fields that are sent change. To remove a spending limit, send
     * {@code "removeSpendingLimit": true}.
     */
    public record UpdateCardRequest(
            @Size(min = 1, max = 50) String nickname,
            @Positive Long spendingLimitCents,
            boolean removeSpendingLimit) {
    }

    public record CardResponse(
            UUID id, UUID accountId, String nickname, String last4, CardStatus status,
            Long spendingLimitCents, Instant createdAt) {

        static CardResponse of(Card card) {
            return new CardResponse(card.getId(), card.getAccountId(), card.getNickname(), card.getLast4(),
                    card.getStatus(), card.getSpendingLimitCents(), card.getCreatedAt());
        }
    }

    private final CardRepository cards;
    private final AccountRepository accounts;

    public CardController(CardRepository cards, AccountRepository accounts) {
        this.cards = cards;
        this.accounts = accounts;
    }

    @PostMapping("/api/accounts/{accountId}/cards")
    @Transactional
    public ResponseEntity<CardResponse> issue(@PathVariable UUID accountId,
                                              @Valid @RequestBody IssueCardRequest request) {
        requireCustomerAccount(accountId);
        Card card = cards.save(Card.issue(accountId, request.nickname().strip(), request.spendingLimitCents()));
        return ResponseEntity.created(URI.create("/api/cards/" + card.getId())).body(CardResponse.of(card));
    }

    @GetMapping("/api/accounts/{accountId}/cards")
    @Transactional(readOnly = true)
    public List<CardResponse> list(@PathVariable UUID accountId) {
        requireCustomerAccount(accountId);
        return cards.findByAccountIdOrderByCreatedAt(accountId).stream().map(CardResponse::of).toList();
    }

    @GetMapping("/api/cards/{id}")
    @Transactional(readOnly = true)
    public CardResponse get(@PathVariable UUID id) {
        return CardResponse.of(find(id));
    }

    @PatchMapping("/api/cards/{id}")
    @Transactional
    public CardResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCardRequest request) {
        Card card = find(id);
        if (request.nickname() != null) {
            card.rename(request.nickname().strip());
        }
        if (request.removeSpendingLimit()) {
            card.setSpendingLimit(null);
        } else if (request.spendingLimitCents() != null) {
            card.setSpendingLimit(request.spendingLimitCents());
        }
        return CardResponse.of(card);
    }

    @PostMapping("/api/cards/{id}/freeze")
    @Transactional
    public CardResponse freeze(@PathVariable UUID id) {
        Card card = find(id);
        card.freeze();
        return CardResponse.of(card);
    }

    @PostMapping("/api/cards/{id}/unfreeze")
    @Transactional
    public CardResponse unfreeze(@PathVariable UUID id) {
        Card card = find(id);
        card.unfreeze();
        return CardResponse.of(card);
    }

    @DeleteMapping("/api/cards/{id}")
    @Transactional
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        find(id).cancel();
        return ResponseEntity.noContent().build();
    }

    private Card find(UUID id) {
        return cards.findById(id).orElseThrow(() -> new NotFoundException("Card", id));
    }

    private void requireCustomerAccount(UUID accountId) {
        accounts.findById(accountId)
                .filter(account -> account.getKind() == AccountKind.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Account", accountId));
    }
}
