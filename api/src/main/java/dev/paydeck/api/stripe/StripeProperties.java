package dev.paydeck.api.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code paydeck.stripe.*} settings. Use test-mode keys (sk_test_…) from
 * the Stripe dashboard: no real money ever moves.
 *
 * @param secretKey      Stripe's secret API key. Empty switches Stripe off.
 * @param webhookSecret  The signing secret of the webhook endpoint (whsec_…).
 * @param publicBaseUrl  Where this API can be reached from a browser, for Stripe's return links.
 * @param appReturnUrl   The app link people are sent to after paying.
 */
@ConfigurationProperties("paydeck.stripe")
public record StripeProperties(String secretKey, String webhookSecret, String publicBaseUrl, String appReturnUrl) {

    public boolean isEnabled() {
        return secretKey != null && !secretKey.isBlank();
    }
}
