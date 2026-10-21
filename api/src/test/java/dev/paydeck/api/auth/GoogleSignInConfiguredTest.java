package dev.paydeck.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.paydeck.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

/** With a Google client ID configured, /api/auth/google starts a real Google sign-in. */
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class GoogleSignInConfiguredTest extends ApiTestSupport {

    private static final String CHALLENGE = Secrets.pkceChallenge("verifier-0123456789-0123456789-0123456789-0123");

    @Test
    void sendsTheBrowserToGoogle() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/api/auth/google").session(session)
                        .param("redirect_uri", "paydeck://auth")
                        .param("code_challenge", CHALLENGE))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/google"));

        String google = mvc.perform(get("/oauth2/authorization/google").session(session))
                .andExpect(status().isFound())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(google).startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                .contains("client_id=test-client-id")
                .contains("scope=openid%20profile%20email");
    }

    @Test
    void onlySendsCodesToTheApp() throws Exception {
        mvc.perform(get("/api/auth/google")
                        .param("redirect_uri", "https://evil.example.com/steal")
                        .param("code_challenge", CHALLENGE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiRequestsStillGet401InsteadOfARedirectToGoogle() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
