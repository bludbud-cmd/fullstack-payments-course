package dev.paydeck.api.stripe;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import dev.paydeck.api.access.AccessPolicy;
import dev.paydeck.api.access.AccountAction;
import dev.paydeck.api.account.Account;
import dev.paydeck.api.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class StripeController {

    /** Stripe's smallest charge is about 0.50 in most currencies. */
    public record CheckoutRequest(@Min(100) @Max(10_000_000) long amountCents) {
    }

    public record CheckoutResponse(String checkoutId, String url) {
    }

    private final StripeTopUpService topUps;
    private final StripeProperties properties;
    private final AccessPolicy access;
    private final JsonMapper json;

    public StripeController(StripeTopUpService topUps, StripeProperties properties, AccessPolicy access,
                            JsonMapper json) {
        this.topUps = topUps;
        this.properties = properties;
        this.access = access;
        this.json = json;
    }

    /** Step 1: the app asks for a payment page and opens {@code url} in the browser. */
    @PostMapping("/api/accounts/{accountId}/top-ups/checkout")
    public ResponseEntity<?> checkout(CurrentUser user, @PathVariable UUID accountId,
                                      @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String key,
                                      @Valid @RequestBody CheckoutRequest request) {
        Account account = access.account(user, accountId, AccountAction.MOVE_MONEY);
        if (!properties.isEnabled()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "Card top-ups are not set up on this server.");
            problem.setTitle("Stripe is not set up");
            problem.setProperty("code", "STRIPE_NOT_CONFIGURED");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
        }
        CheckoutGateway.CheckoutSession session = topUps.start(account, request.amountCents(), key);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CheckoutResponse(session.id(), session.url()));
    }

    /** Step 2: after paying (or giving up), Stripe's page sends the browser here, and we hand over to the app. */
    @GetMapping("/payments/return")
    public ResponseEntity<Void> backToApp(@RequestParam(defaultValue = "cancelled") String result) {
        String safe = result.equals("success") ? "success" : "cancelled";
        URI app = UriComponentsBuilder.fromUriString(properties.appReturnUrl()).queryParam("result", safe)
                .build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(app).build();
    }

    /** Step 3: Stripe tells us, signed, that the payment went through. */
    @PostMapping("/api/stripe/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (!StripeSignature.isValid(payload, signature, properties.webhookSecret(), Instant.now())) {
            return ResponseEntity.badRequest().build();
        }
        JsonNode event = json.readTree(payload);
        JsonNode session = event.path("data").path("object");
        switch (event.path("type").asString()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                // "completed" can arrive before slower payment methods have settled.
                if (session.path("payment_status").asString().equals("paid")) {
                    topUps.paid(session.path("id").asString(), session.path("amount_total").asLong(),
                            session.path("currency").asString());
                }
            }
            case "checkout.session.expired" -> topUps.expired(session.path("id").asString());
            default -> {
                // We only asked for the events above; anything else is fine to ignore.
            }
        }
        return ResponseEntity.ok().build();
    }
}
