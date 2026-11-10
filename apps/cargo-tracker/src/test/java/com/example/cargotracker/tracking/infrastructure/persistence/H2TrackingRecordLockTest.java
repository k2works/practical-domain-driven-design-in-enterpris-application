package com.example.cargotracker.tracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestoneRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.infrastructure.persistence.MyBatisTrackingRecordLockIntegrationTest.Blocked;
import com.example.cargotracker.tracking.infrastructure.persistence.MyBatisTrackingRecordLockIntegrationTest.Fixture;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 同じ追跡番号の DE-13 の同時（Bolt 39g の行 37〜39）を H2 で確かめ、{@code FOR UPDATE} の待ちの上限を記録する（本体は PostgreSQL のテストと共有する。ADR-007）。
 * クラスに {@code @Transactional} を付けない理由は {@link MyBatisTrackingRecordLockIntegrationTest} のとおり。
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2TrackingRecordLockTest {

    private static final Logger LOG = LoggerFactory.getLogger(H2TrackingRecordLockTest.class);

    /** H2 にはロックの待ちの一覧がないので、後の操作が待ちに入るまでの時間だけ止める（前例 H2VoyageConcurrentUpdateTest）。 */
    private static final Blocked HOLD_WHILE_BLOCKED = (jdbc, firstThread) -> await().pollDelay(Duration.ofMillis(300))
            .atMost(Duration.ofSeconds(1))
            .until(() -> true);

    @Autowired
    TrackingRecordRepository repository;

    @Autowired
    RejectedMilestoneRepository rejectedMilestones;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    Clock clock;

    private Fixture fixture() {
        return new Fixture(repository, rejectedMilestones, jdbc, transactionManager, events, clock);
    }

    @Nested
    class Bolt39g_同時 {

        @Test
        void 行37_H2でも2つのDE13が重なると待った側が読み直し実績番号は1と2で版は2増える() throws Exception {
            MyBatisTrackingRecordLockIntegrationTest.assertTwoExternalMilestonesAreSerialized(
                    fixture(), HOLD_WHILE_BLOCKED);
        }

        @ParameterizedTest(name = "追跡記録がある {0}")
        @ValueSource(booleans = {true, false})
        void 行38_H2でも同じDE13の配信が2本重なっても実績か拒否の記録は1件(boolean recordExists) throws Exception {
            MyBatisTrackingRecordLockIntegrationTest.assertSameExternalMilestoneOverlaps(
                    fixture(), recordExists, HOLD_WHILE_BLOCKED);
        }

        @ParameterizedTest(name = "追跡記録がある {0}")
        @ValueSource(booleans = {true, false})
        void 行37_行38_H2でも配信が重なっても発行記録が両方完了する(boolean recordExists) {
            MyBatisTrackingRecordLockIntegrationTest.assertOverlappingDeliveriesComplete(fixture(), recordExists);
        }

        @ParameterizedTest(name = "DE-13 が先 {0}")
        @ValueSource(booleans = {true, false})
        void 行39_H2でも追跡管理者の登録とDE13が重なるとDE13の下書きは必ず残る(boolean externalFirst) throws Exception {
            MyBatisTrackingRecordLockIntegrationTest.assertRegistrationOverlapsExternalMilestone(
                    fixture(), externalFirst, HOLD_WHILE_BLOCKED);
        }

        /** H2 は文の timeout でなく接続のロックの待ちの上限で打ち切る。既定の 2 秒だと PostgreSQL より早く諦め、インメモリの H2 は再起動で再配信もされない。 */
        @Test
        void 行22_H2のロックの待ちの上限はPostgreSQLの文のtimeoutと同じ10秒にする() {
            assertThat(jdbc.queryForObject("SELECT LOCK_TIMEOUT()", Integer.class))
                    .isEqualTo(10_000);
        }

        @Test
        void 行37_H2のFORUPDATEの待ちの上限は例外になり待ちの時間を記録する() throws Exception {
            Fixture f = fixture();
            TrackingNumber number = f.started().trackingNumber();
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> holder = executor.submit(() -> f.transaction().executeWithoutResult(status -> {
                    repository.findByTrackingNumberForExternalMilestone(number);
                    locked.countDown();
                    try {
                        release.await(60, TimeUnit.SECONDS);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                    }
                }));
                assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

                long started = System.nanoTime();
                Future<Throwable> waiter = executor.submit(() -> {
                    try {
                        f.transaction()
                                .executeWithoutResult(
                                        status -> repository.findByTrackingNumberForExternalMilestone(number));
                        return null;
                    } catch (DataAccessException e) {
                        return e;
                    }
                });
                Throwable failure = waiter.get(30, TimeUnit.SECONDS);
                long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                LOG.info("Bolt 39g 行 37: H2 の FOR UPDATE の待ちの上限 {} ms、例外 {}", waitedMillis, String.valueOf(failure));

                assertThat(failure).as("ロックを取れないまま待ち続けず例外になる").isInstanceOf(DataAccessException.class);
                release.countDown();
                holder.get(30, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        }
    }
}
