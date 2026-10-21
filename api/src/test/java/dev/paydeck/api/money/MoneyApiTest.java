package dev.paydeck.api.money;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import dev.paydeck.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MoneyApiTest extends ApiTestSupport {

    @Test
    void aNewAccountStartsEmpty() throws Exception {
        UUID account = openAccount("Asha", "INR");

        mvc.perform(get("/api/accounts/{id}", account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Asha"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.balanceCents").value(0));
    }

    @Test
    void aTopUpAddsToTheBalance() throws Exception {
        UUID account = openAccount("Asha", "USD");

        topUp(account, 2_500);

        mvc.perform(get("/api/accounts/{id}", account)).andExpect(jsonPath("$.balanceCents").value(2_500));
    }

    @Test
    void aTransferMovesMoneyBetweenAccounts() throws Exception {
        UUID from = openAccount("Asha", "USD");
        UUID to = openAccount("Ben", "USD");
        topUp(from, 10_000);

        transfer(from, to, 3_000, UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(header().string("Idempotent-Replayed", "false"));

        mvc.perform(get("/api/accounts/{id}", from)).andExpect(jsonPath("$.balanceCents").value(7_000));
        mvc.perform(get("/api/accounts/{id}", to)).andExpect(jsonPath("$.balanceCents").value(3_000));
        mvc.perform(get("/api/accounts/{id}/transactions", from))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$[0].amountCents").value(-3_000));
    }

    @Test
    void aTransferCannotSpendMoreThanTheBalance() throws Exception {
        UUID from = openAccount("Asha", "USD");
        UUID to = openAccount("Ben", "USD");
        topUp(from, 1_000);

        transfer(from, to, 1_001, UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        mvc.perform(get("/api/accounts/{id}", from)).andExpect(jsonPath("$.balanceCents").value(1_000));
    }

    @Test
    void aTransferNeedsMatchingCurrencies() throws Exception {
        UUID from = openAccount("Asha", "USD");
        UUID to = openAccount("Ben", "EUR");
        topUp(from, 1_000);

        transfer(from, to, 500, UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void retryingWithTheSameKeyDoesNotMoveMoneyTwice() throws Exception {
        UUID from = openAccount("Asha", "USD");
        UUID to = openAccount("Ben", "USD");
        topUp(from, 10_000);
        String key = UUID.randomUUID().toString();

        String firstId = transfer(from, to, 4_000, key).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        transfer(from, to, 4_000, key)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(com.jayway.jsonpath.JsonPath.<String>read(firstId, "$.id")));

        mvc.perform(get("/api/accounts/{id}", from)).andExpect(jsonPath("$.balanceCents").value(6_000));
    }

    @Test
    void reusingAKeyForADifferentRequestIsRefused() throws Exception {
        UUID from = openAccount("Asha", "USD");
        UUID to = openAccount("Ben", "USD");
        topUp(from, 10_000);
        String key = UUID.randomUUID().toString();

        transfer(from, to, 4_000, key).andExpect(status().isCreated());
        transfer(from, to, 5_000, key).andExpect(status().isConflict());

        mvc.perform(get("/api/accounts/{id}", from)).andExpect(jsonPath("$.balanceCents").value(6_000));
    }

    @Test
    void moneyRequestsNeedAnIdempotencyKey() throws Exception {
        UUID account = openAccount("Asha", "USD");

        mvc.perform(post("/api/accounts/{id}/top-ups", account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 100, "description": "No key"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The Idempotency-Key header is required"));
    }

    @Test
    void amountsMustBePositive() throws Exception {
        UUID account = openAccount("Asha", "USD");

        mvc.perform(post("/api/accounts/{id}/top-ups", account)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": -100, "description": "Negative"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amountCents").exists());
    }

    @Test
    void systemAccountsAreHidden() throws Exception {
        mvc.perform(get("/api/accounts/{id}", "00000000-0000-0000-0000-000000000840"))
                .andExpect(status().isNotFound());
    }

    private ResultActions transfer(UUID from, UUID to, long amountCents, String key) throws Exception {
        return mvc.perform(post("/api/transfers")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fromAccountId": "%s", "toAccountId": "%s", "amountCents": %d, "description": "Rent"}
                        """.formatted(from, to, amountCents)));
    }
}
