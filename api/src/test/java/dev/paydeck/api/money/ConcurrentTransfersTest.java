package dev.paydeck.api.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.paydeck.api.ApiTestSupport;
import dev.paydeck.api.common.BusinessRuleException;
import dev.paydeck.api.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Many payments hitting one account at the same moment must never overspend it. */
class ConcurrentTransfersTest extends ApiTestSupport {

    @Autowired
    MoneyMovementService money;

    @Autowired
    LedgerService ledger;

    @Test
    void simultaneousTransfersNeverOverspend() throws Exception {
        UUID from = openAccount("Asha", "USD");
        UUID to = openAccount("Ben", "USD");
        topUp(from, 1_000);

        int attempts = 20;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        money.transfer(from, to, 100, "Coffee", UUID.randomUUID().toString());
                        return true;
                    } catch (BusinessRuleException insufficientFunds) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(10);
        }

        assertThat(ledger.balanceOf(from)).isZero();
        assertThat(ledger.balanceOf(to)).isEqualTo(1_000);
    }

    @Test
    void transfersInOppositeDirectionsDoNotDeadlock() throws Exception {
        UUID a = openAccount("Asha", "USD");
        UUID b = openAccount("Ben", "USD");
        topUp(a, 5_000);
        topUp(b, 5_000);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                boolean aToB = i % 2 == 0;
                results.add(pool.submit(() -> {
                    start.await();
                    money.transfer(aToB ? a : b, aToB ? b : a, 100, "Split bill", UUID.randomUUID().toString());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();
            }
        }

        assertThat(ledger.balanceOf(a) + ledger.balanceOf(b)).isEqualTo(10_000);
    }
}
