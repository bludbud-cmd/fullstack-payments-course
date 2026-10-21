package dev.paydeck.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Shared setup for tests that call the API over HTTP, backed by a real database. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;

    protected UUID openAccount(String owner, String currency) throws Exception {
        String body = mvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerName": "%s", "currency": "%s"}
                                """.formatted(owner, currency)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected void topUp(UUID accountId, long amountCents) throws Exception {
        mvc.perform(post("/api/accounts/{id}/top-ups", accountId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": %d, "description": "Test top-up"}
                                """.formatted(amountCents)))
                .andExpect(status().isCreated());
    }
}
