package dev.paydeck.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Small helpers for making and checking random secrets. */
final class Secrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Secrets() {
    }

    /** 256 random bits, as URL-safe text. Impossible to guess. */
    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** What we store instead of a token: if the database leaks, the hashes are useless. */
    static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    /** The PKCE "S256" challenge for a code verifier (RFC 7636). */
    static String pkceChallenge(String codeVerifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(codeVerifier));
    }

    /** Compares without leaking, through timing, how many characters matched. */
    static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
