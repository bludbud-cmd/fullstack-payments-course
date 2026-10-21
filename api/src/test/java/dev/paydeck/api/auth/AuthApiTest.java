package dev.paydeck.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.paydeck.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuthApiTest extends ApiTestSupport {

    @Test
    void signingUpReturnsTokensThatWork() throws Exception {
        TestUser asha = register("Asha");

        mvc.perform(get("/api/me").with(as(asha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("Asha"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.accounts").isEmpty());
    }

    @Test
    void passwordsAreStoredHashed() throws Exception {
        TestUser asha = register("Asha");

        String stored = users.findById(asha.id()).orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void anEmailCanOnlyBeUsedOnce() throws Exception {
        TestUser asha = register("Asha");

        registerWith(asha.email().toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void shortPasswordsAreRefused() throws Exception {
        registerWith("short@example.com", "hunter2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").value("must be at least 10 characters"));
    }

    @Test
    void signingInWorksWithAnyCapitalisationOfTheEmail() throws Exception {
        TestUser asha = register("Asha");

        TestUser again = login(asha.email().toUpperCase());

        assertThat(again.id()).isEqualTo(asha.id());
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGetTheSameAnswer() throws Exception {
        TestUser asha = register("Asha");

        loginWith(asha.email(), "wrong password!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("That email and password do not match."));
        loginWith("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("That email and password do not match."));
    }

    @Test
    void requestsWithoutATokenAreTurnedAway() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("NOT_SIGNED_IN"));
    }

    @Test
    void forgedTokensAreTurnedAway() throws Exception {
        TestUser asha = register("Asha");
        String[] parts = asha.accessToken().split("\\.");
        // Same header and claims, but the signature of a different token.
        String forged = parts[0] + "." + parts[1] + "." + register("Ben").accessToken().split("\\.")[2];

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRefreshTokenGivesANewPairAndWorksOnlyOnce() throws Exception {
        TestUser asha = register("Asha");

        String body = refresh(asha.refreshToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        TestUser refreshed = toUser(body);
        assertThat(refreshed.refreshToken()).isNotEqualTo(asha.refreshToken());
        mvc.perform(get("/api/me").with(as(refreshed))).andExpect(status().isOk());

        refresh(asha.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void reusingAnOldRefreshTokenSignsOutEverySession() throws Exception {
        TestUser asha = register("Asha");
        TestUser refreshed = toUser(refresh(asha.refreshToken()).andReturn().getResponse().getContentAsString());

        // Someone replays the first token (perhaps it was stolen)...
        refresh(asha.refreshToken()).andExpect(status().isUnauthorized());

        // ...so the newer one stops working too.
        refresh(refreshed.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutEndsTheRefreshToken() throws Exception {
        TestUser asha = register("Asha");

        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(asha.refreshToken())))
                .andExpect(status().isNoContent());

        refresh(asha.refreshToken()).andExpect(status().isUnauthorized());
    }

    private ResultActions registerWith(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "displayName": "Someone"}
                        """.formatted(email, password)));
    }

    private ResultActions loginWith(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }
}
