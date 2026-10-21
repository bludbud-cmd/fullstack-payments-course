package dev.paydeck.api.account;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import dev.paydeck.api.common.NotFoundException;
import dev.paydeck.api.ledger.LedgerService;
import dev.paydeck.api.money.SupportedCurrency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    public record OpenAccountRequest(
            @NotBlank @Size(max = 100) String ownerName,
            @NotNull SupportedCurrency currency) {
    }

    public record AccountResponse(
            UUID id, String ownerName, SupportedCurrency currency, long balanceCents, Instant createdAt) {
    }

    private final AccountRepository accounts;
    private final LedgerService ledger;

    public AccountController(AccountRepository accounts, LedgerService ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = accounts.save(Account.openCustomerAccount(request.ownerName().strip(), request.currency()));
        return ResponseEntity.created(URI.create("/api/accounts/" + account.getId()))
                .body(toResponse(account, 0));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AccountResponse get(@PathVariable UUID id) {
        Account account = accounts.findById(id)
                .filter(a -> a.getKind() == AccountKind.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Account", id));
        return toResponse(account, ledger.balanceOf(id));
    }

    private static AccountResponse toResponse(Account account, long balanceCents) {
        return new AccountResponse(account.getId(), account.getOwnerName(), account.getCurrency(),
                balanceCents, account.getCreatedAt());
    }
}
