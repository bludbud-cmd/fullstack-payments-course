package dev.paydeck.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import dev.paydeck.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The PayDeck side of "Sign in with Google". Google itself is not called in
 * tests: we start from the moment Google has said who the person is.
 */
class GoogleSignInTest extends ApiTestSupport {

    private static final String VERIFIER = "a-random-code-verifier-that-only-the-app-knows-0123456789";

    @Autowired
    AuthService auth;

    @Test
    void saysSoWhenGoogleIsNotSetUp() throws Exception {
        mvc.perform(get("/api/auth/google")
                        .param("redirect_uri", "paydeck://auth")
                        .param("code_challenge", Secrets.pkceChallenge(VERIFIER)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GOOGLE_NOT_CONFIGURED"));
    }

    @Test
    void aNewGoogleUserIsSignedUp() throws Exception {
        String subject = UUID.randomUUID().toString();
        User user = auth.signInWithGoogle(subject, "new-" + subject + "@gmail.com", true, "Chen");

        assertThat(user.getDisplayName()).isEqualTo("Chen");
        assertThat(user.getPasswordHash()).isNull();
        assertThat(auth.signInWithGoogle(subject, "changed@gmail.com", true, "Chen").getId())
                .isEqualTo(user.getId());
    }

    @Test
    void googleIsLinkedToAnExistingAccountWithTheSameVerifiedEmail() throws Exception {
        TestUser asha = register("Asha");

        User viaGoogle = auth.signInWithGoogle(UUID.randomUUID().toString(), asha.email(), true, "Asha G");

        assertThat(viaGoogle.getId()).isEqualTo(asha.id());
    }

    @Test
    void theAppTradesTheCodeAndVerifierForTokens() throws Exception {
        TestUser asha = register("Asha");
        String code = auth.createLoginCode(asha.id(), Secrets.pkceChallenge(VERIFIER));

        exchange(code, VERIFIER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(asha.id().toString()))
                .andExpect(jsonPath("$.accessToken").isString());

        // A code works only once.
        exchange(code, VERIFIER).andExpect(status().isUnauthorized());
    }

    @Test
    void aStolenCodeIsUselessWithoutTheVerifier() throws Exception {
        TestUser asha = register("Asha");
        String code = auth.createLoginCode(asha.id(), Secrets.pkceChallenge(VERIFIER));

        exchange(code, "an-attacker-guessing-the-verifier-will-not-get-it-right-ever")
                .andExpect(status().isUnauthorized());
        // And the failed attempt used the code up.
        exchange(code, VERIFIER).andExpect(status().isUnauthorized());
    }

    private ResultActions exchange(String code, String verifier) throws Exception {
        return mvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s", "codeVerifier": "%s"}
                        """.formatted(code, verifier)));
    }
}
