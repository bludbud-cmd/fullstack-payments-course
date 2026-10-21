package dev.paydeck.api.card;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.paydeck.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CardApiTest extends ApiTestSupport {

    @Test
    void aCardCanBeIssuedFrozenLimitedAndCancelled() throws Exception {
        UUID account = openAccount("Asha", "GBP");

        String issued = mvc.perform(post("/api/accounts/{id}/cards", account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "Groceries", "spendingLimitCents": 5000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.last4").isString())
                .andReturn().getResponse().getContentAsString();
        String card = JsonPath.read(issued, "$.id");

        mvc.perform(post("/api/cards/{id}/freeze", card))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        mvc.perform(post("/api/cards/{id}/freeze", card))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CARD_NOT_ACTIVE"));
        mvc.perform(post("/api/cards/{id}/unfreeze", card))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(patch("/api/cards/{id}", card)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "Food", "removeSpendingLimit": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Food"))
                .andExpect(jsonPath("$.spendingLimitCents").doesNotExist());

        mvc.perform(get("/api/accounts/{id}/cards", account)).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(delete("/api/cards/{id}", card)).andExpect(status().isNoContent());
        mvc.perform(get("/api/cards/{id}", card)).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void anUnknownAccountCannotGetACard() throws Exception {
        mvc.perform(post("/api/accounts/{id}/cards", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "Travel"}
                                """))
                .andExpect(status().isNotFound());
    }
}
