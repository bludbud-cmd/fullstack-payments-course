package dev.paydeck.api.account;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import dev.paydeck.api.access.AccessPolicy;
import dev.paydeck.api.access.AccountAction;
import dev.paydeck.api.auth.CurrentUser;
import dev.paydeck.api.auth.UserRepository;
import dev.paydeck.api.ledger.LedgerService;
import dev.paydeck.api.money.SupportedCurrency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    public record OpenAccountRequest(@NotNull SupportedCurrency currency) {
    }

    public record AccountResponse(
            UUID id, String ownerName, SupportedCurrency currency, long balanceCents, Instant createdAt) {

        public static AccountResponse of(Account account, long balanceCents) {
            return new AccountResponse(account.getId(), account.getOwnerName(), account.getCurrency(),
                    balanceCents, account.getCreatedAt());
        }
    }

    private final AccountRepository accounts;
    private final UserRepository users;
    private final LedgerService ledger;
    private final AccessPolicy access;

    public AccountController(AccountRepository accounts, UserRepository users, LedgerService ledger,
                             AccessPolicy access) {
        this.accounts = accounts;
        this.users = users;
        this.ledger = ledger;
        this.access = access;
    }

    /** Opens an account for the signed-in user, in their name. */
    @PostMapping
    @Transactional
    public ResponseEntity<AccountResponse> open(CurrentUser user, @Valid @RequestBody OpenAccountRequest request) {
        String ownerName = users.findById(user.id()).orElseThrow().getDisplayName();
        Account account = accounts.save(Account.openCustomerAccount(user.id(), ownerName, request.currency()));
        return ResponseEntity.created(URI.create("/api/accounts/" + account.getId()))
                .body(AccountResponse.of(account, 0));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AccountResponse get(CurrentUser user, @PathVariable UUID id) {
        Account account = access.account(user, id, AccountAction.VIEW);
        return AccountResponse.of(account, ledger.balanceOf(id));
    }
}
