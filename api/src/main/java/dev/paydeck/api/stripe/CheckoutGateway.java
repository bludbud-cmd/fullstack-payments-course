package dev.paydeck.api.stripe;

import java.util.UUID;

import dev.paydeck.api.money.SupportedCurrency;

/** Opens a hosted payment page. The real one is Stripe; tests use a fake. */
public interface CheckoutGateway {

    record CheckoutSession(String id, String url) {
    }

    CheckoutSession createTopUpSession(UUID accountId, long amountCents, SupportedCurrency currency,
                                       String idempotencyKey);
}
