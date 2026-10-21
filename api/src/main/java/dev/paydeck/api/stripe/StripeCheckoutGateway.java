package dev.paydeck.api.stripe;

import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.paydeck.api.money.SupportedCurrency;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Creates Stripe Checkout Sessions with a plain HTTP call, so you can see
 * exactly what is sent. Stripe's API takes form fields, with square brackets
 * for nested values.
 * See https://docs.stripe.com/api/checkout/sessions/create
 */
@Component
public class StripeCheckoutGateway implements CheckoutGateway {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StripeSession(String id, String url) {
    }

    private final StripeProperties properties;
    private final RestClient http;

    public StripeCheckoutGateway(StripeProperties properties, RestClient.Builder http) {
        this.properties = properties;
        this.http = http.baseUrl("https://api.stripe.com").build();
    }

    @Override
    public CheckoutSession createTopUpSession(UUID accountId, long amountCents, SupportedCurrency currency,
                                              String idempotencyKey) {
        String returnUrl = properties.publicBaseUrl() + "/payments/return";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", currency.name().toLowerCase(Locale.ROOT));
        form.add("line_items[0][price_data][unit_amount]", Long.toString(amountCents));
        form.add("line_items[0][price_data][product_data][name]", "PayDeck wallet top-up");
        form.add("client_reference_id", accountId.toString());
        form.add("metadata[accountId]", accountId.toString());
        form.add("success_url", returnUrl + "?result=success");
        form.add("cancel_url", returnUrl + "?result=cancelled");

        StripeSession session = http.post()
                .uri("/v1/checkout/sessions")
                .header("Authorization", "Bearer " + properties.secretKey())
                // Stripe understands idempotency keys too, so a retry never opens two pages.
                .header("Idempotency-Key", "paydeck-top-up-" + idempotencyKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(StripeSession.class);
        return new CheckoutSession(session.id(), session.url());
    }
}
