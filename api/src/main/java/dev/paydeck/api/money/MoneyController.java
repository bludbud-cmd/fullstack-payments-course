package dev.paydeck.api.money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.paydeck.api.access.AccessPolicy;
import dev.paydeck.api.access.AccountAction;
import dev.paydeck.api.auth.CurrentUser;
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
import dev.paydeck.api.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
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
    private final AccessPolicy access;
    private final LedgerService ledger;
    private final boolean simulatedTopUps;

    public MoneyController(MoneyMovementService money, AccessPolicy access, LedgerService ledger,
                           @Value("${paydeck.simulated-top-ups:true}") boolean simulatedTopUps) {
        this.money = money;
        this.access = access;
        this.ledger = ledger;
        this.simulatedTopUps = simulatedTopUps;
    }

    @PostMapping("/api/accounts/{accountId}/top-ups")
    public ResponseEntity<TransactionResponse> topUp(
            CurrentUser user,
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody TopUpRequest request) {
        access.account(user, accountId, AccountAction.MOVE_MONEY);
        if (!simulatedTopUps) {
            throw new BusinessRuleException("SIMULATED_TOP_UPS_OFF", "Top up with a card instead.");
        }
        MovementResult result = money.topUp(accountId, request.amountCents(), request.description().strip(),
                idempotencyKey);
        return respond(result, request.amountCents());
    }

    @PostMapping("/api/transfers")
    public ResponseEntity<TransactionResponse> transfer(
            CurrentUser user,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        // Only the owner can send money out. Anyone can receive it.
        access.account(user, request.fromAccountId(), AccountAction.MOVE_MONEY);
        MovementResult result = money.transfer(request.fromAccountId(), request.toAccountId(),
                request.amountCents(), request.description().strip(), idempotencyKey);
        return respond(result, request.amountCents());
    }

    @GetMapping("/api/accounts/{accountId}/transactions")
    public List<AccountActivity> history(CurrentUser user, @PathVariable UUID accountId) {
        access.account(user, accountId, AccountAction.VIEW);
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
