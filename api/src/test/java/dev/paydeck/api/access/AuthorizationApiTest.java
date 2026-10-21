package dev.paydeck.api.access;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.paydeck.api.ApiTestSupport;
import dev.paydeck.api.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** The rules in AccessPolicy, tried against the real API by different people. */
class AuthorizationApiTest extends ApiTestSupport {

    @Test
    void customersCannotSeeEachOthersAccounts() throws Exception {
        UUID ashas = openAccount("Asha", "USD");
        TestUser ben = register("Ben");

        // "Not found", not "not allowed": Ben must not even learn the account exists.
        mvc.perform(get("/api/accounts/{id}", ashas).with(as(ben))).andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/{id}/transactions", ashas).with(as(ben))).andExpect(status().isNotFound());
        mvc.perform(post("/api/accounts/{id}/top-ups", ashas).with(as(ben))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 100, "description": "Not mine"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void nobodyCanSendMoneyFromSomeoneElsesAccount() throws Exception {
        UUID ashas = openAccount("Asha", "USD");
        UUID bens = openAccount("Ben", "USD");
        topUp(ashas, 5_000);

        mvc.perform(post("/api/transfers").with(asOwnerOf(bens))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": "%s", "toAccountId": "%s", "amountCents": 5000, "description": "Mine now"}
                                """.formatted(ashas, bens)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/{id}", ashas).with(asOwnerOf(ashas)))
                .andExpect(jsonPath("$.balanceCents").value(5_000));
    }

    @Test
    void aCardholderCanUseButNotManageACardTheyWereGiven() throws Exception {
        UUID ashas = openAccount("Asha", "USD");
        TestUser ben = register("Ben");
        String card = issueCard(ashas, "For Ben", ben.email());

        mvc.perform(get("/api/me").with(as(ben)))
                .andExpect(jsonPath("$.heldCards[0].id").value(card))
                .andExpect(jsonPath("$.accounts", hasSize(0)));
        mvc.perform(get("/api/cards/{id}", card).with(as(ben))).andExpect(status().isOk());
        mvc.perform(get("/api/accounts/{id}", ashas).with(as(ben))).andExpect(status().isNotFound());

        mvc.perform(post("/api/cards/{id}/freeze", card).with(as(ben)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        mvc.perform(post("/api/cards/{id}/unfreeze", card).with(as(ben)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(patch("/api/cards/{id}", card).with(as(ben))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spendingLimitCents": 99999999}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/cards/{id}/unfreeze", card).with(asOwnerOf(ashas))).andExpect(status().isOk());
    }

    @Test
    void cardsCanOnlyBeGivenToPeopleWhoHaveSignedUp() throws Exception {
        UUID ashas = openAccount("Asha", "USD");

        mvc.perform(post("/api/accounts/{id}/cards", ashas).with(asOwnerOf(ashas))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "Gift", "cardholderEmail": "nobody-here@example.com"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CARDHOLDER_NOT_FOUND"));
    }

    @Test
    void supportCanLookAndFreezeButNeverMoveMoney() throws Exception {
        UUID ashas = openAccount("Asha", "USD");
        String card = issueCard(ashas, "Everyday", null);
        TestUser support = registerAs("Sam", Role.SUPPORT);

        mvc.perform(get("/api/accounts/{id}", ashas).with(as(support))).andExpect(status().isOk());
        mvc.perform(post("/api/cards/{id}/freeze", card).with(as(support))).andExpect(status().isOk());
        mvc.perform(post("/api/accounts/{id}/top-ups", ashas).with(as(support))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 100, "description": "Helping"}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/cards/{id}", card).with(as(support))).andExpect(status().isForbidden());
    }

    @Test
    void customersCannotUseStaffTools() throws Exception {
        TestUser asha = register("Asha");

        mvc.perform(get("/api/admin/users").with(as(asha)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void onlyAdminsChangeRolesAndReadTheAuditLog() throws Exception {
        TestUser support = registerAs("Sam", Role.SUPPORT);
        TestUser admin = registerAs("Ada", Role.ADMIN);
        TestUser asha = register("Asha");

        mvc.perform(get("/api/admin/users").param("email", asha.email()).with(as(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(asha.id().toString()));
        mvc.perform(get("/api/admin/audit").with(as(support))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/{id}/role", asha.id()).with(as(support))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/admin/users/{id}/role", asha.id()).with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "SUPPORT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPPORT"));
        mvc.perform(get("/api/admin/audit").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("ROLE_CHANGED")));
    }

    @Test
    void adminsCannotChangeTheirOwnRole() throws Exception {
        TestUser admin = registerAs("Ada", Role.ADMIN);

        mvc.perform(put("/api/admin/users/{id}/role", admin.id()).with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "CUSTOMER"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ROLE"));
    }

    private String issueCard(UUID accountId, String nickname, String cardholderEmail) throws Exception {
        String holder = cardholderEmail == null ? "null" : "\"" + cardholderEmail + "\"";
        String body = mvc.perform(post("/api/accounts/{id}/cards", accountId).with(asOwnerOf(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "%s", "cardholderEmail": %s}
                                """.formatted(nickname, holder)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
