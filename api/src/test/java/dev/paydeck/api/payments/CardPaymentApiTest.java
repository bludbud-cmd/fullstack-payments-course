package dev.paydeck.api.payments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.paydeck.api.ApiTestSupport;
import dev.paydeck.api.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class CardPaymentApiTest extends ApiTestSupport {

    @Test
    void anApprovedPaymentTakesMoneyFromTheWallet() throws Exception {
        UUID account = openAccount("Asha", "USD");
        topUp(account, 5_000);
        UUID card = issueCard(account, "Everyday", null, null);

        pay(asOwnerOf(account), card, 1_250, "Corner Café", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.declineReason").doesNotExist());

        balanceIs(account, 3_750);
        mvc.perform(get("/api/accounts/{id}/transactions", account).with(asOwnerOf(account)))
                .andExpect(jsonPath("$[0].type").value("CARD_PAYMENT"))
                .andExpect(jsonPath("$[0].description").value("Corner Café"))
                .andExpect(jsonPath("$[0].amountCents").value(-1_250));
    }

    @Test
    void paymentsOverTheLimitAreDeclined() throws Exception {
        UUID account = openAccount("Asha", "USD");
        topUp(account, 50_000);
        UUID card = issueCard(account, "Groceries", 10_000L, null);

        pay(asOwnerOf(account), card, 10_001, "Big Shop", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("OVER_LIMIT"))
                .andExpect(jsonPath("$.declineMessage").value("The payment is over the card's spending limit."));
        balanceIs(account, 50_000);
    }

    @Test
    void frozenCardsAndEmptyWalletsAreDeclined() throws Exception {
        UUID account = openAccount("Asha", "USD");
        topUp(account, 1_000);
        UUID card = issueCard(account, "Everyday", null, null);

        pay(asOwnerOf(account), card, 1_001, "Shop", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.declineReason").value("INSUFFICIENT_FUNDS"));

        mvc.perform(post("/api/cards/{id}/freeze", card).with(asOwnerOf(account))).andExpect(status().isOk());
        pay(asOwnerOf(account), card, 100, "Shop", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.declineReason").value("CARD_FROZEN"));

        mvc.perform(get("/api/cards/{id}/payments", card).with(asOwnerOf(account)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].declineReason").value("CARD_FROZEN"));
    }

    @Test
    void retryingAPaymentWithTheSameKeyChargesOnce() throws Exception {
        UUID account = openAccount("Asha", "USD");
        topUp(account, 5_000);
        UUID card = issueCard(account, "Everyday", null, null);
        String key = UUID.randomUUID().toString();

        String first = pay(asOwnerOf(account), card, 2_000, "Taxi", key)
                .andReturn().getResponse().getContentAsString();
        pay(asOwnerOf(account), card, 2_000, "Taxi", key)
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(first, "$.id")));
        pay(asOwnerOf(account), card, 9_000, "Taxi", key).andExpect(status().isConflict());

        balanceIs(account, 3_000);
    }

    @Test
    void aCardholderCanPayButAStrangerCannot() throws Exception {
        UUID account = openAccount("Asha", "USD");
        topUp(account, 5_000);
        TestUser ben = register("Ben");
        UUID card = issueCard(account, "For Ben", null, ben.email());

        pay(as(ben), card, 500, "Bakery", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        pay(as(register("Mallory")), card, 500, "Bakery", UUID.randomUUID().toString())
                .andExpect(status().isNotFound());
        balanceIs(account, 4_500);
    }

    @Test
    void onlyStaffCanRefund() throws Exception {
        UUID account = openAccount("Asha", "USD");
        topUp(account, 5_000);
        UUID card = issueCard(account, "Everyday", null, null);
        String paid = pay(asOwnerOf(account), card, 2_000, "Gadget Shop", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString();
        String payment = JsonPath.read(paid, "$.id");

        mvc.perform(post("/api/admin/card-payments/{id}/refund", payment).with(asOwnerOf(account)))
                .andExpect(status().isForbidden());

        TestUser support = registerAs("Sam", Role.SUPPORT);
        mvc.perform(post("/api/admin/card-payments/{id}/refund", payment).with(as(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mvc.perform(post("/api/admin/card-payments/{id}/refund", payment).with(as(support)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_REFUNDABLE"));
        balanceIs(account, 5_000);
    }

    @Test
    void cardTopUpsSaySoWhenStripeIsNotSetUp() throws Exception {
        UUID account = openAccount("Asha", "USD");
        mvc.perform(post("/api/accounts/{id}/top-ups/checkout", account).with(asOwnerOf(account))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 2500}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STRIPE_NOT_CONFIGURED"));
    }

    private ResultActions pay(org.springframework.test.web.servlet.request.RequestPostProcessor who, UUID card,
                              long amountCents, String merchant, String key) throws Exception {
        return mvc.perform(post("/api/cards/{id}/payments", card).with(who)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amountCents": %d, "merchant": "%s"}
                        """.formatted(amountCents, merchant)));
    }

    private void balanceIs(UUID account, long cents) throws Exception {
        mvc.perform(get("/api/accounts/{id}", account).with(asOwnerOf(account)))
                .andExpect(jsonPath("$.balanceCents").value(cents));
    }
}
