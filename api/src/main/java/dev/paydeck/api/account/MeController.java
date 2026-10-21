package dev.paydeck.api.account;

import java.util.List;

import dev.paydeck.api.account.AccountController.AccountResponse;
import dev.paydeck.api.auth.AuthController.UserResponse;
import dev.paydeck.api.auth.CurrentUser;
import dev.paydeck.api.auth.UserRepository;
import dev.paydeck.api.card.CardController.CardResponse;
import dev.paydeck.api.card.CardRepository;
import dev.paydeck.api.ledger.LedgerService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Who am I, and what is mine?" The app calls this right after signing in. */
@RestController
public class MeController {

    public record MeResponse(UserResponse user, List<AccountResponse> accounts, List<CardResponse> heldCards) {
    }

    private final UserRepository users;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final LedgerService ledger;

    public MeController(UserRepository users, AccountRepository accounts, CardRepository cards,
                        LedgerService ledger) {
        this.users = users;
        this.accounts = accounts;
        this.cards = cards;
        this.ledger = ledger;
    }

    /**
     * The accounts this person owns, and the cards other people have given
     * them (cards on their own accounts are listed under each account).
     */
    @GetMapping("/api/me")
    @Transactional(readOnly = true)
    public MeResponse me(CurrentUser current) {
        UserResponse user = UserResponse.of(users.findById(current.id()).orElseThrow());
        List<AccountResponse> owned = accounts.findByOwnerUserIdOrderByCreatedAt(current.id()).stream()
                .map(account -> AccountResponse.of(account, ledger.balanceOf(account.getId())))
                .toList();
        List<CardResponse> held = cards.findByCardholderUserIdOrderByCreatedAt(current.id()).stream()
                .map(CardResponse::of)
                .toList();
        return new MeResponse(user, owned, held);
    }
}
