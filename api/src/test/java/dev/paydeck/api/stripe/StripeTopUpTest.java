package dev.paydeck.api.stripe;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import dev.paydeck.api.ApiTestSupport;
import dev.paydeck.api.money.SupportedCurrency;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/** Top-ups through Stripe Checkout. Stripe itself is replaced: the test plays both Stripe's API and its webhook. */
@TestPropertySource(properties = {
        "paydeck.stripe.secret-key=sk_test_fake",
        "paydeck.stripe.webhook-secret=" + StripeTopUpTest.WEBHOOK_SECRET,
        "paydeck.simulated-top-ups=false"
})
class StripeTopUpTest extends ApiTestSupport {

    static final String WEBHOOK_SECRET = "whsec_test_paydeck";

    @MockitoBean
    CheckoutGateway stripe;

    @Test
    void theWalletIsCreditedOnlyWhenStripeSaysItWasPaid() throws Exception {
        UUID account = openAccount("Asha", "EUR");
        String sessionId = "cs_test_" + UUID.randomUUID();
        when(stripe.createTopUpSession(eq(account), eq(2_500L), eq(SupportedCurrency.EUR), anyString()))
                .thenReturn(new CheckoutGateway.CheckoutSession(sessionId, "https://checkout.stripe.com/c/pay/x"));

        mvc.perform(post("/api/accounts/{id}/top-ups/checkout", account).with(asOwnerOf(account))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 2500}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/pay/x"));
        balanceIs(account, 0);

        String paid = completedEvent(sessionId, 2_500, "eur");
        webhook(paid).andExpect(status().isOk());
        balanceIs(account, 2_500);

        // Stripe retries webhooks, sometimes long after: the money must still arrive only once.
        webhook(paid).andExpect(status().isOk());
        balanceIs(account, 2_500);
        mvc.perform(get("/api/accounts/{id}/transactions", account).with(asOwnerOf(account)))
                .andExpect(jsonPath("$[0].description").value("Card top-up"));
    }

    @Test
    void aForgedWebhookIsRefused() throws Exception {
        UUID account = openAccount("Asha", "USD");
        String sessionId = startCheckout(account, 1_000);

        String body = completedEvent(sessionId, 1_000, "usd");
        long now = Instant.now().getEpochSecond();
        mvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", "t=" + now + ",v1=" + StripeSignature.sign(now + "." + body,
                                "whsec_guessed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        balanceIs(account, 0);
    }

    @Test
    void anAmountThatDoesNotMatchIsNotCredited() throws Exception {
        UUID account = openAccount("Asha", "USD");
        String sessionId = startCheckout(account, 1_000);

        webhook(completedEvent(sessionId, 999_999, "usd")).andExpect(status().isOk());
        balanceIs(account, 0);
    }

    @Test
    void theBrowserIsSentBackToTheApp() throws Exception {
        mvc.perform(get("/payments/return").param("result", "success"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "paydeck://top-up?result=success"));
        mvc.perform(get("/payments/return").param("result", "javascript:alert(1)"))
                .andExpect(header().string("Location", "paydeck://top-up?result=cancelled"));
    }

    @Test
    void pretendTopUpsAreOffOnceStripeIsLive() throws Exception {
        UUID account = openAccount("Asha", "USD");
        mvc.perform(post("/api/accounts/{id}/top-ups", account).with(asOwnerOf(account))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 100, "description": "Free money"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SIMULATED_TOP_UPS_OFF"));
    }

    private String startCheckout(UUID account, long amountCents) throws Exception {
        String sessionId = "cs_test_" + UUID.randomUUID();
        when(stripe.createTopUpSession(eq(account), anyLong(), any(), anyString()))
                .thenReturn(new CheckoutGateway.CheckoutSession(sessionId, "https://checkout.stripe.com/x"));
        mvc.perform(post("/api/accounts/{id}/top-ups/checkout", account).with(asOwnerOf(account))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": %d}
                                """.formatted(amountCents)))
                .andExpect(status().isCreated());
        return sessionId;
    }

    private static String completedEvent(String sessionId, long amountTotal, String currency) {
        return """
                {"id": "evt_%s", "type": "checkout.session.completed",
                 "data": {"object": {"id": "%s", "object": "checkout.session", "payment_status": "paid",
                                     "amount_total": %d, "currency": "%s"}}}
                """.formatted(UUID.randomUUID(), sessionId, amountTotal, currency);
    }

    private ResultActions webhook(String body) throws Exception {
        long now = Instant.now().getEpochSecond();
        return mvc.perform(post("/api/stripe/webhook")
                .header("Stripe-Signature", "t=" + now + ",v1=" + StripeSignature.sign(now + "." + body,
                        WEBHOOK_SECRET))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void balanceIs(UUID account, long cents) throws Exception {
        mvc.perform(get("/api/accounts/{id}", account).with(asOwnerOf(account)))
                .andExpect(jsonPath("$.balanceCents").value(cents));
    }
}
