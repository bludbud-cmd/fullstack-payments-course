package dev.paydeck.api.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import dev.paydeck.api.card.Card;
import org.junit.jupiter.api.Test;

class DeclineRulesTest {

    @Test
    void approvesWhenEverythingIsFine() {
        Card card = Card.issue(UUID.randomUUID(), "Everyday", 5_000L);
        assertThat(CardPaymentService.declineReason(card, 10_000, 5_000)).isNull();
    }

    @Test
    void aFrozenCardIsReportedBeforeAnythingElse() {
        Card card = Card.issue(UUID.randomUUID(), "Everyday", 100L);
        card.freeze();
        assertThat(CardPaymentService.declineReason(card, 0, 5_000)).isEqualTo(DeclineReason.CARD_FROZEN);
    }

    @Test
    void theLimitIsCheckedBeforeTheBalance() {
        Card card = Card.issue(UUID.randomUUID(), "Everyday", 100L);
        assertThat(CardPaymentService.declineReason(card, 0, 101)).isEqualTo(DeclineReason.OVER_LIMIT);
    }

    @Test
    void aCancelledCardNeverPays() {
        Card card = Card.issue(UUID.randomUUID(), "Old", null);
        card.cancel();
        assertThat(CardPaymentService.declineReason(card, 1_000_000, 1)).isEqualTo(DeclineReason.CARD_CANCELLED);
    }
}
