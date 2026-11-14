package com.example.cargotracker.tracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 受付番号の同時採番（US-20 の AC1。W8 の持ち越し）。複数のトランザクションが同時に採番しても、番号は重ならず、欠けずに連番になる。
 * 実際にコミットして競合させるので、{@code @Transactional} を付けない（既存の統合テストと別のクラス）。その年の行がまだない状態で
 * 同時に始めるので、年の行の作成の競合も通る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisServiceCaseNumberIssuerConcurrencyIntegrationTest {

    /** 他のテストが使わない年。 */
    private static final int YEAR = 2094;

    private static final int THREADS = 8;
    private static final int PER_THREAD = 5;

    @Autowired
    ServiceCaseNumberIssuer issuer;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tracking.service_case_number_counter WHERE number_year = ?", YEAR);
    }

    @Test
    void 同時に採番しても番号は重ならず欠けずに連番になる() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                Callable<List<String>> task = () -> {
                    start.await();
                    List<String> numbers = new ArrayList<>();
                    for (int i = 0; i < PER_THREAD; i++) {
                        // 起票と同じく、1 回の採番を 1 つのトランザクションに入れてコミットする
                        CaseNumber number = tx.execute(status -> issuer.next(YEAR));
                        numbers.add(number.value());
                    }
                    return numbers;
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<String> all = new ArrayList<>();
            for (Future<List<String>> future : futures) {
                all.addAll(future.get());
            }

            int total = THREADS * PER_THREAD;
            List<String> expected = new ArrayList<>();
            for (int n = 1; n <= total; n++) {
                expected.add(CaseNumber.of(YEAR, n).value());
            }
            assertThat(all).hasSize(total).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
        } finally {
            pool.shutdownNow();
        }
    }
}
