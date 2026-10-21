package dev.paydeck.api.money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.paydeck.api.account.AccountKind;
import dev.paydeck.api.account.AccountRepository;
import dev.paydeck.api.common.NotFoundException;
import dev.paydeck.api.ledger.AccountActivity;
import dev.paydeck.api.ledger.LedgerService;
import dev.paydeck.api.ledger.LedgerTransaction;
import dev.paydeck.api.ledger.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MoneyController {

    /** The most a single top-up or transfer can move: 100,000.00 in the account's currency. */
    static final long MAX_AMOUNT_CENTS = 10_000_000;

    public record TopUpRequest(
            @Positive @Max(MAX_AMOUNT_CENTS) long amountCents,
            @NotBlank @Size(max = 140) String description) {
    }

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @Positive @Max(MAX_AMOUNT_CENTS) long amountCents,
            @NotBlank @Size(max = 140) String description) {
    }

    public record TransactionResponse(
            UUID id, TransactionType type, String description, long amountCents, Instant createdAt) {
    }

    private final MoneyMovementService money;
    private final AccountRepository accounts;
    private final LedgerService ledger;

    public MoneyController(MoneyMovementService money, AccountRepository accounts, LedgerService ledger) {
        this.money = money;
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @PostMapping("/api/accounts/{accountId}/top-ups")
    public ResponseEntity<TransactionResponse> topUp(
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody TopUpRequest request) {
        MovementResult result = money.topUp(accountId, request.amountCents(), request.description().strip(),
                idempotencyKey);
        return respond(result, request.amountCents());
    }

    @PostMapping("/api/transfers")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        MovementResult result = money.transfer(request.fromAccountId(), request.toAccountId(),
                request.amountCents(), request.description().strip(), idempotencyKey);
        return respond(result, request.amountCents());
    }

    @GetMapping("/api/accounts/{accountId}/transactions")
    public List<AccountActivity> history(@PathVariable UUID accountId) {
        accounts.findById(accountId)
                .filter(account -> account.getKind() == AccountKind.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Account", accountId));
        return ledger.activityOf(accountId);
    }

    /** A repeated request gets the original answer with 200 instead of 201, and a header saying so. */
    private static ResponseEntity<TransactionResponse> respond(MovementResult result, long amountCents) {
        LedgerTransaction transaction = result.transaction();
        TransactionResponse body = new TransactionResponse(transaction.getId(), transaction.getType(),
                transaction.getDescription(), amountCents, transaction.getCreatedAt());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(body);
    }
}
