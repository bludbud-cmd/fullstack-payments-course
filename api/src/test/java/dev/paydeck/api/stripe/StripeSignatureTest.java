package dev.paydeck.api.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class StripeSignatureTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String BODY = "{\"type\":\"checkout.session.completed\"}";
    private static final Instant NOW = Instant.ofEpochSecond(1_800_000_000);

    private static String header(long timestamp, String body, String secret) {
        return "t=" + timestamp + ",v1=" + StripeSignature.sign(timestamp + "." + body, secret);
    }

    @Test
    void acceptsAGenuineSignature() {
        assertThat(StripeSignature.isValid(BODY, header(NOW.getEpochSecond(), BODY, SECRET), SECRET, NOW)).isTrue();
    }

    @Test
    void refusesAChangedBody() {
        String header = header(NOW.getEpochSecond(), BODY, SECRET);
        assertThat(StripeSignature.isValid(BODY.replace("completed", "expired"), header, SECRET, NOW)).isFalse();
    }

    @Test
    void refusesTheWrongSecret() {
        assertThat(StripeSignature.isValid(BODY, header(NOW.getEpochSecond(), BODY, "whsec_other"), SECRET, NOW))
                .isFalse();
    }

    @Test
    void refusesAnOldReplayedEvent() {
        long tenMinutesAgo = NOW.minusSeconds(600).getEpochSecond();
        assertThat(StripeSignature.isValid(BODY, header(tenMinutesAgo, BODY, SECRET), SECRET, NOW)).isFalse();
    }

    @Test
    void refusesAMissingOrGarbledHeader() {
        assertThat(StripeSignature.isValid(BODY, null, SECRET, NOW)).isFalse();
        assertThat(StripeSignature.isValid(BODY, "nonsense", SECRET, NOW)).isFalse();
        assertThat(StripeSignature.isValid(BODY, "t=abc,v1=00", SECRET, NOW)).isFalse();
    }
}
