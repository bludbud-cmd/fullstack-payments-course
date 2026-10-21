package dev.paydeck.api.stripe;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Checks that a webhook really came from Stripe. Anyone can send a request
 * to our webhook URL, so without this check anyone could tell PayDeck "this
 * top-up was paid" and get free money.
 *
 * <p>Stripe sends a {@code Stripe-Signature} header like
 * {@code t=1700000000,v1=5257a8…}: an HMAC-SHA256 of "timestamp.body" made
 * with the webhook secret that only Stripe and we know.
 * See https://docs.stripe.com/webhooks#verify-manually
 */
public final class StripeSignature {

    /** Older events are refused, so a recorded request cannot be replayed later. */
    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private StripeSignature() {
    }

    public static boolean isValid(String payload, String header, String secret, Instant now) {
        if (header == null || secret == null || secret.isBlank()) {
            return false;
        }
        Long timestamp = null;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if (keyValue[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(keyValue[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (keyValue[0].equals("v1")) {
                signatures.add(keyValue[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            return false;
        }
        if (Duration.between(Instant.ofEpochSecond(timestamp), now).abs().compareTo(TOLERANCE) > 0) {
            return false;
        }
        byte[] expected = sign(timestamp + "." + payload, secret).getBytes(StandardCharsets.UTF_8);
        return signatures.stream()
                .anyMatch(signature -> MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8)));
    }

    /** Exposed for tests, which play the part of Stripe. */
    public static String sign(String signedPayload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
