package dev.paydeck.api.stripe;

import java.util.UUID;

import dev.paydeck.api.account.Account;
import dev.paydeck.api.money.MoneyMovementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Topping up with a real (test) card through Stripe Checkout:
 * <ol>
 *   <li>{@link #start} asks Stripe for a payment page and remembers it.</li>
 *   <li>The person pays on Stripe's page, in the browser.</li>
 *   <li>Stripe calls our webhook; {@link #paid} credits the wallet.</li>
 * </ol>
 * The wallet is only ever credited from the webhook, never because the app
 * says "I paid": the app could be lying, Stripe's signed webhook cannot.
 */
@Service
public class StripeTopUpService {

    private static final Logger log = LoggerFactory.getLogger(StripeTopUpService.class);

    private final CheckoutGateway gateway;
    private final StripeCheckoutRepository checkouts;
    private final MoneyMovementService money;

    public StripeTopUpService(CheckoutGateway gateway, StripeCheckoutRepository checkouts,
                              MoneyMovementService money) {
        this.gateway = gateway;
        this.checkouts = checkouts;
        this.money = money;
    }

    @Transactional
    public CheckoutGateway.CheckoutSession start(Account account, long amountCents, String idempotencyKey) {
        CheckoutGateway.CheckoutSession session = gateway.createTopUpSession(account.getId(), amountCents,
                account.getCurrency(), idempotencyKey);
        if (!checkouts.existsById(session.id())) {
            checkouts.save(new StripeCheckout(session.id(), account.getId(), amountCents, account.getCurrency()));
        }
        return session;
    }

    /**
     * Stripe can send the same event more than once, so this must be safe to
     * repeat. The Checkout Session ID doubles as the ledger's idempotency key.
     */
    @Transactional
    public void paid(String sessionId, long amountTotal, String currency) {
        StripeCheckout checkout = checkouts.findByIdForUpdate(sessionId).orElse(null);
        if (checkout == null) {
            log.warn("Stripe reported a payment for a checkout we never opened: {}", sessionId);
            return;
        }
        if (checkout.getStatus() == StripeCheckout.Status.PAID) {
            return;
        }
        if (amountTotal != checkout.getAmountCents() || !checkout.getCurrency().name().equalsIgnoreCase(currency)) {
            log.error("Stripe amount {} {} does not match checkout {}; not crediting", amountTotal, currency,
                    sessionId);
            return;
        }
        UUID accountId = checkout.getAccountId();
        money.topUp(accountId, checkout.getAmountCents(), "Card top-up", "stripe:" + sessionId);
        checkout.markPaid();
    }

    @Transactional
    public void expired(String sessionId) {
        checkouts.findByIdForUpdate(sessionId)
                .filter(checkout -> checkout.getStatus() == StripeCheckout.Status.OPEN)
                .ifPresent(StripeCheckout::markExpired);
    }
}
