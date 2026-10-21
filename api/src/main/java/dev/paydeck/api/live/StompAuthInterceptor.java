package dev.paydeck.api.live;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.paydeck.api.access.AccessPolicy;
import dev.paydeck.api.access.AccountAction;
import dev.paydeck.api.auth.CurrentUser;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The WebSocket version of sign-in and permissions. CONNECT must carry an
 * {@code Authorization: Bearer …} header, and each SUBSCRIBE is checked
 * against the same rules as the REST API.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern ACCOUNT_TOPIC = Pattern.compile("^/topic/accounts/([0-9a-fA-F-]{36})$");
    private static final Pattern USER_TOPIC = Pattern.compile("^/topic/users/([0-9a-fA-F-]{36})$");

    private final JwtDecoder jwtDecoder;
    private final AccessPolicy access;

    public StompAuthInterceptor(@Lazy JwtDecoder jwtDecoder, @Lazy AccessPolicy access) {
        this.jwtDecoder = jwtDecoder;
        this.access = access;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        switch (stomp.getCommand()) {
            case CONNECT -> stomp.setUser(authenticate(stomp.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> checkSubscription(stomp);
            case SEND -> throw new MessageDeliveryException("PayDeck only sends updates; it does not accept them");
            default -> {
            }
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Sign in to continue.");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
            return new JwtAuthenticationToken(jwt);
        } catch (JwtException e) {
            throw new MessageDeliveryException("Sign in to continue.");
        }
    }

    private void checkSubscription(StompHeaderAccessor stomp) {
        if (!(stomp.getUser() instanceof JwtAuthenticationToken token)) {
            throw new MessageDeliveryException("Sign in to continue.");
        }
        CurrentUser user = CurrentUser.from(token.getToken());
        String destination = stomp.getDestination() == null ? "" : stomp.getDestination();

        Matcher account = ACCOUNT_TOPIC.matcher(destination);
        if (account.matches()) {
            // Throws if this person may not see the account, which refuses the subscription.
            access.account(user, UUID.fromString(account.group(1)), AccountAction.VIEW);
            return;
        }
        Matcher person = USER_TOPIC.matcher(destination);
        if (person.matches() && UUID.fromString(person.group(1)).equals(user.id())) {
            return;
        }
        throw new MessageDeliveryException("You cannot subscribe to " + destination);
    }
}
