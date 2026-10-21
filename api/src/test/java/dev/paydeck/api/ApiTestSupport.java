package dev.paydeck.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.jayway.jsonpath.JsonPath;
import dev.paydeck.api.auth.Role;
import dev.paydeck.api.auth.User;
import dev.paydeck.api.auth.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Shared setup for tests that call the API over HTTP, backed by a real database. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct horse battery";

    /** A signed-up user and the tokens they got. */
    protected record TestUser(UUID id, String email, String accessToken, String refreshToken) {
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected UserRepository users;

    private final Map<UUID, TestUser> owners = new ConcurrentHashMap<>();

    /** Signs up someone new. Every call gets a fresh email, so tests never clash. */
    protected TestUser register(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "displayName": "%s"}
                                """.formatted(email, PASSWORD, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return toUser(body);
    }

    /** Signs up someone and gives them a staff role, then signs in again so their token carries it. */
    protected TestUser registerAs(String name, Role role) throws Exception {
        TestUser user = register(name);
        User stored = users.findById(user.id()).orElseThrow();
        stored.changeRole(role);
        users.save(stored);
        return login(user.email());
    }

    protected TestUser login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return toUser(body);
    }

    protected static TestUser toUser(String authResponse) {
        return new TestUser(UUID.fromString(JsonPath.read(authResponse, "$.user.id")),
                JsonPath.read(authResponse, "$.user.email"),
                JsonPath.read(authResponse, "$.accessToken"),
                JsonPath.read(authResponse, "$.refreshToken"));
    }

    /** Sends the request with this user's access token. */
    protected static RequestPostProcessor as(TestUser user) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + user.accessToken());
            return request;
        };
    }

    protected RequestPostProcessor asOwnerOf(UUID accountId) {
        return as(ownerOf(accountId));
    }

    protected TestUser ownerOf(UUID accountId) {
        return owners.get(accountId);
    }

    protected UUID openAccount(TestUser owner, String currency) throws Exception {
        String body = mvc.perform(post("/api/accounts")
                        .with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency": "%s"}
                                """.formatted(currency)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(JsonPath.read(body, "$.id"));
        owners.put(accountId, owner);
        return accountId;
    }

    /** Signs up a new person called {@code owner} and opens an account for them. */
    protected UUID openAccount(String owner, String currency) throws Exception {
        return openAccount(register(owner), currency);
    }

    protected void topUp(UUID accountId, long amountCents) throws Exception {
        mvc.perform(post("/api/accounts/{id}/top-ups", accountId)
                        .with(asOwnerOf(accountId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": %d, "description": "Test top-up"}
                                """.formatted(amountCents)))
                .andExpect(status().isCreated());
    }
}
