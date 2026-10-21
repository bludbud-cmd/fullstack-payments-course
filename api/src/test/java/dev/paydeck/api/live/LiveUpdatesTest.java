package dev.paydeck.api.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import dev.paydeck.api.ApiTestSupport;
import dev.paydeck.api.push.PushSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Live updates over a real WebSocket, and the push notifications that go with them. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveUpdatesTest extends ApiTestSupport {

    @LocalServerPort
    int port;

    @MockitoBean
    PushSender push;

    private WebSocketStompClient client;

    @AfterEach
    void stopClient() {
        if (client != null) {
            client.stop();
        }
    }

    @Test
    void theOwnerHearsAboutATopUpStraightAway() throws Exception {
        UUID account = openAccount("Asha", "USD");
        StompSession session = connect(ownerOf(account).accessToken());
        BlockingQueue<Map<String, Object>> received = subscribe(session, "/topic/accounts/" + account);

        topUp(account, 2_500);

        Map<String, Object> message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("kind")).isEqualTo("MONEY_IN");
        assertThat(message.get("title")).isEqualTo("Money added");
        assertThat(((Number) message.get("balanceCents")).longValue()).isEqualTo(2_500);
    }

    @Test
    void aCardholderHearsAboutPaymentsOnTheirOwnTopic() throws Exception {
        UUID account = openAccount("Asha", "USD");
        topUp(account, 5_000);
        TestUser ben = register("Ben");
        UUID card = issueCard(account, "For Ben", 1_000L, ben.email());
        StompSession session = connect(ben.accessToken());
        BlockingQueue<Map<String, Object>> received = subscribe(session, "/topic/users/" + ben.id());

        mvc.perform(post("/api/cards/{id}/payments", card).with(as(ben))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 2000, "merchant": "Bike Shop"}
                                """))
                .andExpect(status().isCreated());

        Map<String, Object> message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("kind")).isEqualTo("CARD_DECLINED");
        assertThat((String) message.get("body")).contains("over the card's spending limit");
    }

    @Test
    void nobodyCanListenToSomeoneElsesAccount() throws Exception {
        UUID account = openAccount("Asha", "USD");
        TestUser mallory = register("Mallory");
        CompletableFuture<String> error = new CompletableFuture<>();
        StompSession session = connect(mallory.accessToken(), error);

        session.subscribe("/topic/accounts/" + account, frames(new LinkedBlockingQueue<>()));

        // The server answers the SUBSCRIBE with an ERROR frame and closes the connection.
        assertThat(error.get(5, TimeUnit.SECONDS)).isNotBlank();
    }

    @Test
    void connectingWithoutATokenIsRefused() {
        assertThatThrownBy(() -> connect("not-a-real-token")).isInstanceOf(ExecutionException.class);
    }

    @Test
    void registeredPhonesGetAPushNotification() throws Exception {
        when(push.send(anyString(), anyString(), anyString())).thenReturn(PushSender.Result.SENT);
        UUID account = openAccount("Asha", "GBP");
        registerPhone(account, "phone-token-" + UUID.randomUUID());
        String token = "phone-token-" + UUID.randomUUID();
        registerPhone(account, token);

        topUp(account, 1_050);

        verify(push, timeout(5_000)).send(eq(token), eq("Money added"), contains("£10.50"));
    }

    @Test
    void phonesThatWereUninstalledAreForgotten() throws Exception {
        String token = "gone-" + UUID.randomUUID();
        when(push.send(eq(token), anyString(), anyString())).thenReturn(PushSender.Result.TOKEN_GONE);
        UUID account = openAccount("Asha", "USD");
        registerPhone(account, token);

        topUp(account, 100);
        verify(push, timeout(5_000)).send(eq(token), anyString(), anyString());
        Mockito.clearInvocations(push);

        topUp(account, 100);
        Thread.sleep(500);
        verify(push, Mockito.never()).send(eq(token), anyString(), anyString());
    }

    @Test
    void peopleCanTurnNotificationsOff() throws Exception {
        UUID account = openAccount("Asha", "USD");
        String token = "quiet-" + UUID.randomUUID();
        registerPhone(account, token);
        mvc.perform(put("/api/me/notifications").with(asOwnerOf(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payments": false, "declines": true}
                                """))
                .andExpect(status().isOk());

        topUp(account, 100);
        Thread.sleep(500);
        verify(push, Mockito.never()).send(eq(token), anyString(), anyString());
    }

    private void registerPhone(UUID account, String token) throws Exception {
        mvc.perform(post("/api/devices").with(asOwnerOf(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"platform": "ANDROID", "token": "%s"}
                                """.formatted(token)))
                .andExpect(status().isNoContent());
    }

    private StompSession connect(String accessToken) throws Exception {
        return connect(accessToken, new CompletableFuture<>());
    }

    private StompSession connect(String accessToken, CompletableFuture<String> errors) throws Exception {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                errors.complete(String.valueOf(headers.getFirst("message")));
                            }

                            @Override
                            public void handleException(StompSession s, StompCommand command, StompHeaders headers,
                                                        byte[] payload, Throwable exception) {
                                errors.complete(exception.getMessage());
                            }

                            @Override
                            public void handleTransportError(StompSession s, Throwable exception) {
                                errors.complete(exception.getMessage());
                            }
                        })
                .get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String topic) throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(topic, frames(received));
        // SUBSCRIBE has no reply in STOMP; give the broker a moment to register it.
        Thread.sleep(300);
        return received;
    }

    private static StompFrameHandler frames(BlockingQueue<Map<String, Object>> into) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                into.add((Map<String, Object>) payload);
            }
        };
    }
}
