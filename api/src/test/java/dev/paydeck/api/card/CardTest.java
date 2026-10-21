package dev.paydeck.api.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import dev.paydeck.api.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

class CardTest {

    private final Card card = Card.issue(UUID.randomUUID(), "Groceries", null);

    @Test
    void aNewCardIsActiveWithFourDigits() {
        assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(card.getLast4()).matches("\\d{4}");
    }

    @Test
    void canBeFrozenAndUnfrozen() {
        card.freeze();
        assertThat(card.getStatus()).isEqualTo(CardStatus.FROZEN);
        card.unfreeze();
        assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    void cannotUnfreezeAnActiveCard() {
        assertThatThrownBy(card::unfreeze)
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CARD_NOT_FROZEN");
    }

    @Test
    void aCancelledCardCannotChange() {
        card.cancel();
        assertThatThrownBy(() -> card.rename("Travel")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(card::freeze).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(card::cancel).isInstanceOf(BusinessRuleException.class);
    }
}
