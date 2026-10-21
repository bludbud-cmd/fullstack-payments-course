package dev.paydeck.api.ledger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LedgerServiceTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void acceptsPostingsThatAddUpToZero() {
        assertThatCode(() -> LedgerService.checkBalanced(
                List.of(new Posting(alice, -500), new Posting(bob, 500))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPostingsThatWouldCreateMoney() {
        assertThatThrownBy(() -> LedgerService.checkBalanced(
                List.of(new Posting(alice, -500), new Posting(bob, 600))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add up to 100");
    }

    @Test
    void rejectsASinglePosting() {
        assertThatThrownBy(() -> LedgerService.checkBalanced(List.of(new Posting(alice, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroPostings() {
        assertThatThrownBy(() -> LedgerService.checkBalanced(
                List.of(new Posting(alice, 0), new Posting(bob, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be zero");
    }
}
