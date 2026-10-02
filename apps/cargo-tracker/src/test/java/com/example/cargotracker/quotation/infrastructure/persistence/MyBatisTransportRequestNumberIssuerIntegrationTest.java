package com.example.cargotracker.quotation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 業務番号の採番を PostgreSQL で確かめる（D-10、データモデル「業務番号の採番」、仮説 H1）。
 * テストごとに別の年を使い、ほかのテストの採番と混ざらないようにする。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisTransportRequestNumberIssuerIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    TransportRequestNumberIssuer issuer;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void 年の最初の採番で1を振り同じ年では続きの番号を振る() {
        assertThat(nextInTransaction(2091)).isEqualTo(new TransportRequestNumber(2091, 1));
        assertThat(nextInTransaction(2091)).isEqualTo(new TransportRequestNumber(2091, 2));
        assertThat(nextInTransaction(2092)).isEqualTo(new TransportRequestNumber(2092, 1));
    }

    @Test
    void 提出のトランザクションが戻れば番号も戻り欠番が出ない() {
        assertThat(nextInTransaction(2093)).isEqualTo(new TransportRequestNumber(2093, 1));

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(issuer.next(2093)).isEqualTo(new TransportRequestNumber(2093, 2));
            status.setRollbackOnly();
        });

        assertThat(nextInTransaction(2093)).isEqualTo(new TransportRequestNumber(2093, 2));
    }

    @Test
    void 年の最初の採番を同時に行っても番号は重複せず連続する() throws Exception {
        assertConcurrentNumbersAreDistinctAndConsecutive(2094);
    }

    @Test
    void 年の行がある状態で同時に採番しても番号は重複せず連続する() throws Exception {
        nextInTransaction(2095);

        List<Integer> sequences = concurrentSequences(2095, 8);

        assertThat(sequences).containsExactlyInAnyOrder(2, 3, 4, 5, 6, 7, 8, 9);
    }

    private void assertConcurrentNumbersAreDistinctAndConsecutive(int year) throws Exception {
        List<Integer> sequences = concurrentSequences(year, 8);

        assertThat(sequences).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
    }

    /** 開始をラッチでそろえ、別々のトランザクションで同時に採番する。 */
    private List<Integer> concurrentSequences(int year, int threads) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<Integer>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> CompletableFuture.supplyAsync(
                            () -> {
                                awaitQuietly(start);
                                return nextInTransaction(year).sequence();
                            },
                            executor))
                    .toList();
            start.countDown();
            return futures.stream()
                    .map(future -> future.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                            .join())
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private TransportRequestNumber nextInTransaction(int year) {
        return transactionTemplate.execute(status -> issuer.next(year));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new IllegalStateException("同時実行の開始を待てませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
