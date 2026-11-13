package com.example.cargotracker.tracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.shared.OverlappingTransactions;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.MilestoneAdoptionOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.MilestoneRegistrationOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingRecordCommandService;
import com.example.cargotracker.tracking.application.internal.eventhandlers.ExternalMilestoneEventHandler;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordScope;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestone;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestoneRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DE-13 の listener が追跡番号の行を押さえて読むことと、同じ追跡番号の同時を PostgreSQL 18 で確かめる（Bolt 39g の行 28・37〜40。決定 (4)。前例
 * {@code MyBatisRoutingCaseLockIntegrationTest}、{@code MyBatisVoyageConcurrentUpdateIntegrationTest}）。本体は H2 のテストと共有する。クラスに
 * {@code @Transactional} を付けない（別のトランザクションで確かめる）。追跡番号はテストごとに乱数で作る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisTrackingRecordLockIntegrationTest {

    /** ロックの確かめには有人例外案件の起票は要らない（起票は RejectedMilestoneCaseOpenerTest。Bolt 40-13）。 */
    private static final com.example.cargotracker.tracking.domain.model.aggregates.RejectionCaseOpener NO_CASE =
            (rejected, trackingRecord, operator) -> {};

    @Autowired
    TrackingRecordRepository repository;

    @Autowired
    RejectedMilestoneRepository rejectedMilestones;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    Clock clock;

    private Fixture fixture() {
        return new Fixture(repository, rejectedMilestones, jdbc, transactionManager, events, clock);
    }

    @Nested
    class Bolt39g_行のロック {

        @Test
        void 行28_押さえて読んでいる間は追跡記録の更新がそのコミットまで待つ() {
            TrackingRecord saved = fixture().started();

            List<Long> versions = OverlappingTransactions.run(
                    transactionTemplate,
                    () -> repository
                            .findByTrackingNumberForUpdate(saved.trackingNumber())
                            .orElseThrow()
                            .aggregateVersion(),
                    () -> {
                        TrackingRecord loaded = repository
                                .findByTrackingNumber(saved.trackingNumber())
                                .orElseThrow();
                        repository.update(loaded);
                        return loaded.aggregateVersion();
                    });

            assertThat(versions).containsExactly(0L, 0L);
        }

        @Test
        void 行28_更新がコミットする前に押さえて読むとそのコミットを待って更新の後の追跡記録を読む() {
            TrackingRecord saved = fixture().started();

            List<Long> versions = OverlappingTransactions.run(
                    transactionTemplate,
                    () -> {
                        TrackingRecord loaded = repository
                                .findByTrackingNumber(saved.trackingNumber())
                                .orElseThrow();
                        repository.update(loaded);
                        return loaded.aggregateVersion();
                    },
                    () -> repository
                            .findByTrackingNumberForUpdate(saved.trackingNumber())
                            .orElseThrow()
                            .aggregateVersion());

            assertThat(versions).containsExactly(0L, 1L);
        }

        @Test
        void 行28_ない追跡番号は押さえずに空を返す() {
            Optional<TrackingRecord> found = transactionTemplate.execute(
                    status -> repository.findByTrackingNumberForUpdate(fixture().randomNumber()));

            assertThat(found).isEmpty();
        }

        @Test
        void 行28_呼び手のトランザクションの外では押さえて読まずに例外にする() {
            TrackingNumber number = fixture().started().trackingNumber();

            assertThatThrownBy(() -> repository.findByTrackingNumberForUpdate(number))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
    }

    @Nested
    class Bolt39g_同時 {

        private final Blocked blocked = (jdbc, firstThread) -> await("後の操作の接続が先の接続のロックを待っている")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                                        + " AND query LIKE '%tracking.%'",
                                Integer.class)
                        > 0);

        @Test
        void 行37_同じ追跡番号の2つのDE13が重なると待った側が読み直し実績番号は1と2で版は2増える() throws Exception {
            assertTwoExternalMilestonesAreSerialized(fixture(), blocked);
        }

        @ParameterizedTest(name = "追跡記録がある {0}")
        @ValueSource(booleans = {true, false})
        void 行38_同じDE13の配信が2本重なっても実績か拒否の記録は1件(boolean recordExists) throws Exception {
            assertSameExternalMilestoneOverlaps(fixture(), recordExists, blocked);
        }

        @ParameterizedTest(name = "追跡記録がある {0}")
        @ValueSource(booleans = {true, false})
        void 行37_行38_配信が重なっても発行記録が両方完了する(boolean recordExists) {
            assertOverlappingDeliveriesComplete(fixture(), recordExists);
        }

        @ParameterizedTest(name = "DE-13 が先 {0}")
        @ValueSource(booleans = {true, false})
        void 行39_追跡管理者の登録とDE13が重なるとDE13の下書きは必ず残る(boolean externalFirst) throws Exception {
            assertRegistrationOverlapsExternalMilestone(fixture(), externalFirst, blocked);
        }

        @Test
        void 行40_別の追跡番号はロックを待たない() throws Exception {
            Fixture f = fixture();
            TrackingNumber held = f.started().trackingNumber();
            TrackingNumber other = f.started().trackingNumber();
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> holder = executor.submit(() -> f.transaction().executeWithoutResult(status -> {
                    f.repository().findByTrackingNumberForUpdate(held);
                    locked.countDown();
                    awaitQuietly(release);
                }));
                assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

                f.deliver(f.handler(), Fixture.event(other, UUID.randomUUID().toString(), "MS-1"));

                assertThat(f.milestones(other)).hasSize(1);
                assertThat(holder).isNotDone();
                release.countDown();
                holder.get(30, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        }
    }

    /**
     * Bolt 39g4b の行 15（決定 (7)）。同じ追跡記録に DE-12 と DE-13 の受け取りが重なると行のロックで直列になり、両方の結果が残る。画面の登録は DE-12 の
     * 受け取りの前に読んだ版で保存すると今のとおり競合。最後の区間の航海はテストごとに作る（ほかのテストがコミットした追跡を押さえない）。
     */
    @Nested
    class Bolt39g4b_DE12の直列 {

        private final Blocked blocked = (jdbc, firstThread) -> await("後の受け取りの接続が先の受け取りのロックを待っている")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                                        + " AND query LIKE '%tracking.%'",
                                Integer.class)
                        > 0);

        private TrackingRecord startedOn(Fixture f, String finalVoyage) {
            TrackingRecord trackingRecord = TrackingRecord.start(
                            f.randomNumber(),
                            UUID.randomUUID(),
                            new CompanyId(UUID.randomUUID()),
                            new CompanyId(UUID.randomUUID()),
                            new com.example.cargotracker.tracking.domain.model.valueobjects.Schedule(
                                    "RC-2026-0001",
                                    1,
                                    List.of(
                                            TrackingFixture.leg(
                                                    "V100",
                                                    "JPTYO",
                                                    "KRPUS",
                                                    "2026-11-01T00:00:00Z",
                                                    "2026-11-03T00:00:00Z"),
                                            TrackingFixture.leg(
                                                    finalVoyage,
                                                    "KRPUS",
                                                    "USLAX",
                                                    "2026-11-04T00:00:00Z",
                                                    "2026-11-15T00:00:00Z"))),
                            TrackingFixture.STARTED_AT)
                    .trackingRecord();
            f.transaction().executeWithoutResult(status -> f.repository().save(trackingRecord));
            return trackingRecord;
        }

        private static com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated voyageEvent(
                String voyage) {
            UtcInstant now = new UtcInstant(Instant.parse("2026-11-06T00:00:00Z"));
            return new com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), now),
                    voyage,
                    List.of(
                            new com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated
                                    .AdoptedPortCall(new Location("KRPUS"), null, now),
                            new com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated
                                    .AdoptedPortCall(
                                    new Location("USLAX"),
                                    new UtcInstant(Instant.parse("2026-11-15T10:00:00Z")),
                                    null)),
                    "1",
                    new UserId(UUID.randomUUID()),
                    new CompanyId(UUID.randomUUID()),
                    now);
        }

        @Test
        void 行15_DE12とDE13の受け取りが重なると直列になり両方の結果が残る() throws Exception {
            Fixture f = fixture();
            String voyage = "V" + f.randomNumber().value().substring(2);
            TrackingNumber number = startedOn(f, voyage).trackingNumber();
            CountDownLatch updated = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            com.example.cargotracker.tracking.application.internal.eventhandlers.VoyageArrivalEventHandler paused =
                    new com.example.cargotracker.tracking.application.internal.eventhandlers.VoyageArrivalEventHandler(
                            f.pausingAfterUpdate(updated, release));

            overlap(
                    f,
                    blocked,
                    updated,
                    release,
                    () -> {
                        f.transaction().executeWithoutResult(status -> paused.on(voyageEvent(voyage)));
                        return null;
                    },
                    () -> {
                        f.deliver(
                                f.handler(),
                                Fixture.event(number, UUID.randomUUID().toString(), "MS-1"));
                        return null;
                    },
                    (a, b) -> {});

            TrackingRecord found = f.repository().findByTrackingNumber(number).orElseThrow();
            assertThat(found.voyageArrival()).isPresent();
            assertThat(found.latestEta()).isEqualTo(new UtcInstant(Instant.parse("2026-11-15T10:00:00Z")));
            assertThat(found.milestones())
                    .singleElement()
                    .satisfies(milestone -> assertThat(milestone.state()).isEqualTo(MilestoneState.DRAFT));
            assertThat(found.aggregateVersion()).isEqualTo(2L);
        }

        @Test
        void 行15_DE12の受け取りの前に読んだ版の画面の登録は競合() {
            Fixture f = fixture();
            String voyage = "V" + f.randomNumber().value().substring(2);
            TrackingRecord saved = startedOn(f, voyage);
            UserId manager = new UserId(UUID.randomUUID());
            UtcInstant at = new UtcInstant(Instant.parse("2026-01-01T00:00:00Z"));
            TrackingRecordCommandService service = new TrackingRecordCommandService(
                    f.repository(), f.clock(), new TrackingRecordScope(f.repository()), published -> {});

            f.transaction()
                    .executeWithoutResult(status ->
                            new com.example.cargotracker.tracking.application.internal.eventhandlers
                                            .VoyageArrivalEventHandler(f.repository())
                                    .on(voyageEvent(voyage)));
            MilestoneRegistrationOutcome outcome = f.transaction()
                    .execute(status -> service.registerMilestone(
                            new RegisterMilestoneCommand(
                                    saved.trackingNumber(),
                                    saved.aggregateVersion(),
                                    MilestoneKind.PICKUP,
                                    new Location("JPTYO"),
                                    at,
                                    new Source(SourceKind.FIELD_RECORD, "F-1", at),
                                    manager),
                            AuthenticatedActorFixture.trackingManagerHandling(manager, saved.shipperCompanyId())));

            assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.Conflict());
        }
    }

    /** 後の操作が先のロックを待っていることを確かめる（PostgreSQL は pg_stat_activity、H2 は一定時間の待ち）。 */
    @FunctionalInterface
    interface Blocked {
        void await(JdbcTemplate jdbc, Thread firstThread) throws Exception;
    }

    /** Bolt 39gc の行 21（Bolt 39gb の行 57 の持ち越し。39gb 終了報告の既知の課題）。 */
    @Nested
    class Bolt39gc_同時の採用 {

        private final Blocked blocked = (jdbc, firstThread) -> await("後の採用の接続が先の採用のロックを待っている")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                                        + " AND query LIKE '%tracking.%'",
                                Integer.class)
                        > 0);

        @Test
        void 行21_同じ候補の採用が2本重なると片方が確認中に採用しもう片方は競合でDE18は1件() throws Exception {
            Fixture f = fixture();
            TrackingRecord saved = f.started();
            TrackingNumber number = saved.trackingNumber();
            UserId manager = new UserId(UUID.randomUUID());
            // 発生時刻は過去の固定の日時にする（実の時計より前。日付で振る舞いが変わらない）
            UtcInstant arrivedAt = new UtcInstant(Instant.parse("2026-01-01T00:00:00Z"));
            UtcInstant departedAt = new UtcInstant(Instant.parse("2026-01-02T00:00:00Z"));
            f.transaction()
                    .executeWithoutResult(status -> f.repository()
                            .update(saved.registerMilestone(
                                            MilestoneKind.ARRIVAL,
                                            new Location("USLAX"),
                                            arrivedAt,
                                            new Source(SourceKind.FIELD_RECORD, "F-1", arrivedAt),
                                            manager,
                                            arrivedAt)
                                    .trackingRecord()));
            // 到着より後の発生時刻の出発（段が前）の下書き。採用すると時系列に反する
            f.deliver(
                    f.handler(),
                    new MilestoneAdoptedValueAccepted(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            Fixture.DATA_SOURCE,
                            new Source(
                                    SourceKind.EXTERNAL_RECORD,
                                    UUID.randomUUID().toString(),
                                    departedAt),
                            number.value(),
                            "DEPARTURE",
                            new Location("JPTYO"),
                            departedAt,
                            "MS-1",
                            "1",
                            Fixture.STEWARD,
                            new CompanyId(UUID.randomUUID()),
                            departedAt));
            long version =
                    f.repository().findByTrackingNumber(number).orElseThrow().aggregateVersion();
            List<Object> published = new CopyOnWriteArrayList<>();
            CountDownLatch written = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            TrackingRecordCommandService paused = new TrackingRecordCommandService(
                    f.pausingAfterUpdate(written, release),
                    f.clock(),
                    new TrackingRecordScope(f.repository()),
                    published::add);
            TrackingRecordCommandService other = new TrackingRecordCommandService(
                    f.repository(), f.clock(), new TrackingRecordScope(f.repository()), published::add);
            AuthenticatedActor actor =
                    AuthenticatedActorFixture.trackingManagerHandling(manager, saved.shipperCompanyId());

            overlap(
                    f,
                    blocked,
                    written,
                    release,
                    () -> f.transaction().execute(status -> paused.adoptCandidate(number, 2, version, actor)),
                    () -> f.transaction().execute(status -> other.adoptCandidate(number, 2, version, actor)),
                    (first, second) -> {
                        assertThat(first)
                                .isEqualTo(new MilestoneAdoptionOutcome.Adopted(
                                        2, MilestoneState.UNDER_REVIEW, TrackingStatus.UNDER_REVIEW));
                        assertThat(second).isEqualTo(new MilestoneAdoptionOutcome.Conflict());
                    });

            assertThat(published).hasSize(1);
            assertThat(f.milestones(number).get(1).state()).isEqualTo(MilestoneState.UNDER_REVIEW);
        }
    }

    /** H2 と PostgreSQL で共有する部品（Bolt 39g）。 */
    record Fixture(
            TrackingRecordRepository repository,
            RejectedMilestoneRepository rejected,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            ApplicationEventPublisher events,
            Clock clock) {

        private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        private static final Random RANDOM = new Random();
        private static final UUID DATA_SOURCE = UUID.fromString("00000000-0000-0000-0000-000000003931");
        private static final UserId STEWARD = new UserId(UUID.fromString("00000000-0000-0000-0000-000000003932"));
        private static final UtcInstant OCCURRED = new UtcInstant(Instant.parse("2026-11-19T14:00:00Z"));

        TransactionTemplate transaction() {
            return new TransactionTemplate(transactionManager);
        }

        TrackingNumber randomNumber() {
            StringBuilder value = new StringBuilder("CT");
            for (int i = 0; i < 12; i++) {
                value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            return new TrackingNumber(value.toString());
        }

        TrackingRecord started() {
            TrackingRecord trackingRecord = TrackingRecord.start(
                            randomNumber(),
                            UUID.randomUUID(),
                            new CompanyId(UUID.randomUUID()),
                            new CompanyId(UUID.randomUUID()),
                            TrackingFixture.schedule(),
                            TrackingFixture.STARTED_AT)
                    .trackingRecord();
            transaction().executeWithoutResult(status -> repository.save(trackingRecord));
            return trackingRecord;
        }

        static MilestoneAdoptedValueAccepted event(TrackingNumber number, String reference, String externalId) {
            return new MilestoneAdoptedValueAccepted(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    DATA_SOURCE,
                    new Source(SourceKind.EXTERNAL_RECORD, reference, OCCURRED),
                    number.value(),
                    "DEPARTURE",
                    new Location("JPTYO"),
                    OCCURRED,
                    externalId,
                    "1",
                    STEWARD,
                    new CompanyId(UUID.randomUUID()),
                    OCCURRED);
        }

        ExternalMilestoneEventHandler handler() {
            return new ExternalMilestoneEventHandler(repository, rejected, clock, NO_CASE);
        }

        void deliver(ExternalMilestoneEventHandler target, MilestoneAdoptedValueAccepted event) {
            transaction().executeWithoutResult(status -> target.on(event));
        }

        List<Milestone> milestones(TrackingNumber number) {
            return repository.findByTrackingNumber(number).orElseThrow().milestones();
        }

        int rejectedCount(String reference) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tracking.rejected_milestone WHERE source_ref = ?", Integer.class, reference);
        }

        int completedPublications(String reference) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM platform.event_publication WHERE listener_id LIKE '%ExternalMilestoneEventHandler%'"
                            + " AND serialized_event LIKE ? AND completion_date IS NOT NULL",
                    Integer.class, "%" + reference + "%");
        }

        /** update の後でコミットの前に止まるリポジトリ（先の操作の行ロックを保ったまま、後の操作を待たせる）。 */
        TrackingRecordRepository pausingAfterUpdate(CountDownLatch updated, CountDownLatch release) {
            TrackingRecordRepository spy =
                    Mockito.mock(TrackingRecordRepository.class, AdditionalAnswers.delegatesTo(repository));
            Mockito.doAnswer(invocation -> {
                        repository.update(invocation.getArgument(0));
                        updated.countDown();
                        awaitQuietly(release);
                        return null;
                    })
                    .when(spy)
                    .update(Mockito.any());
            return spy;
        }

        /** 拒否の記録の追加の後でコミットの前に止まるリポジトリ。 */
        RejectedMilestoneRepository pausingAfterReject(CountDownLatch saved, CountDownLatch release) {
            RejectedMilestoneRepository spy =
                    Mockito.mock(RejectedMilestoneRepository.class, AdditionalAnswers.delegatesTo(rejected));
            Mockito.doAnswer(invocation -> {
                        rejected.saveIfAbsent(invocation.<RejectedMilestone>getArgument(0));
                        saved.countDown();
                        awaitQuietly(release);
                        return null;
                    })
                    .when(spy)
                    .saveIfAbsent(Mockito.any());
            return spy;
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("同時の操作がそろいませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** 先の操作を止めて後の操作を待たせ、先を完了させてから両方の結果を待つ。 */
    private static <A, B> void overlap(
            Fixture f,
            Blocked blocked,
            CountDownLatch firstReady,
            CountDownLatch release,
            Callable<A> first,
            Callable<B> second,
            java.util.function.BiConsumer<A, B> assertions)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<A> firstResult = executor.submit(first);
            assertThat(firstReady.await(30, TimeUnit.SECONDS))
                    .as("先の操作が書き込みまで進んだ")
                    .isTrue();
            Future<B> secondResult = executor.submit(second);
            blocked.await(f.jdbc(), Thread.currentThread());
            assertThat(secondResult).as("後の操作は先の行ロックを待っていて終わっていない").isNotDone();
            release.countDown();
            assertions.accept(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /** 行 37。 */
    static void assertTwoExternalMilestonesAreSerialized(Fixture f, Blocked blocked) throws Exception {
        TrackingNumber number = f.started().trackingNumber();
        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExternalMilestoneEventHandler paused = new ExternalMilestoneEventHandler(
                f.pausingAfterUpdate(updated, release), f.rejected(), f.clock(), NO_CASE);

        overlap(
                f,
                blocked,
                updated,
                release,
                () -> {
                    f.deliver(paused, Fixture.event(number, UUID.randomUUID().toString(), "MS-1"));
                    return null;
                },
                () -> {
                    f.deliver(
                            f.handler(), Fixture.event(number, UUID.randomUUID().toString(), "MS-2"));
                    return null;
                },
                (a, b) -> {});

        assertThat(f.milestones(number)).extracting(Milestone::milestoneNo).containsExactly(1, 2);
        assertThat(f.repository().findByTrackingNumber(number).orElseThrow().aggregateVersion())
                .isEqualTo(2L);
    }

    /** 行 38。 */
    static void assertSameExternalMilestoneOverlaps(Fixture f, boolean recordExists, Blocked blocked) throws Exception {
        TrackingNumber number = recordExists ? f.started().trackingNumber() : f.randomNumber();
        String reference = UUID.randomUUID().toString();
        MilestoneAdoptedValueAccepted event = Fixture.event(number, reference, "MS-1");
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExternalMilestoneEventHandler paused = recordExists
                ? new ExternalMilestoneEventHandler(
                        f.pausingAfterUpdate(written, release), f.rejected(), f.clock(), NO_CASE)
                : new ExternalMilestoneEventHandler(
                        f.repository(), f.pausingAfterReject(written, release), f.clock(), NO_CASE);

        overlap(
                f,
                blocked,
                written,
                release,
                () -> {
                    f.deliver(paused, event);
                    return null;
                },
                () -> {
                    f.deliver(f.handler(), event);
                    return null;
                },
                (a, b) -> {});

        if (recordExists) {
            assertThat(f.milestones(number)).hasSize(1);
        } else {
            assertThat(f.rejectedCount(reference)).isEqualTo(1);
        }
    }

    /** 行 37・38 の発行記録（配信の経路で 2 本を同時に流し、ロックの待ちで失敗しないこと）。 */
    static void assertOverlappingDeliveriesComplete(Fixture f, boolean recordExists) {
        TrackingNumber number = recordExists ? f.started().trackingNumber() : f.randomNumber();
        String first = UUID.randomUUID().toString();
        MilestoneAdoptedValueAccepted same = Fixture.event(number, first, "MS-1");

        f.transaction().executeWithoutResult(status -> {
            f.events().publishEvent(same);
            f.events().publishEvent(same);
        });

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(f.completedPublications(first)).isEqualTo(2));
        if (recordExists) {
            assertThat(f.milestones(number)).hasSize(1);
        } else {
            assertThat(f.rejectedCount(first)).isEqualTo(1);
        }
    }

    /** 行 39。 */
    static void assertRegistrationOverlapsExternalMilestone(Fixture f, boolean externalFirst, Blocked blocked)
            throws Exception {
        TrackingRecord saved = f.started();
        TrackingNumber number = saved.trackingNumber();
        MilestoneAdoptedValueAccepted event =
                Fixture.event(number, UUID.randomUUID().toString(), "MS-1");
        UserId manager = new UserId(UUID.randomUUID());
        // 発生時刻は実の時計より前にする（追跡管理者の登録は未来の発生時刻を拒否する）
        UtcInstant at = new UtcInstant(Instant.parse("2026-01-01T00:00:00Z"));
        RegisterMilestoneCommand command = new RegisterMilestoneCommand(
                number,
                saved.aggregateVersion(),
                MilestoneKind.PICKUP,
                new Location("JPTYO"),
                at,
                new Source(SourceKind.FIELD_RECORD, "F-1", at),
                manager);
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TrackingRecordRepository pausing = f.pausingAfterUpdate(written, release);
        TrackingRecordRepository registrationRepository = externalFirst ? f.repository() : pausing;
        TrackingRecordCommandService service = new TrackingRecordCommandService(
                registrationRepository, f.clock(), new TrackingRecordScope(f.repository()), published -> {});
        Callable<MilestoneRegistrationOutcome> register = () -> f.transaction()
                .execute(status -> service.registerMilestone(
                        command, AuthenticatedActorFixture.trackingManagerHandling(manager, saved.shipperCompanyId())));
        ExternalMilestoneEventHandler handler = externalFirst
                ? new ExternalMilestoneEventHandler(pausing, f.rejected(), f.clock(), NO_CASE)
                : f.handler();
        Callable<MilestoneRegistrationOutcome> deliver = () -> {
            f.deliver(handler, event);
            return null;
        };

        if (externalFirst) {
            overlap(
                    f,
                    blocked,
                    written,
                    release,
                    deliver,
                    register,
                    (a, b) -> assertThat(b).isEqualTo(new MilestoneRegistrationOutcome.Conflict()));
            assertThat(f.milestones(number))
                    .singleElement()
                    .satisfies(milestone -> assertThat(milestone.state()).isEqualTo(MilestoneState.DRAFT));
        } else {
            overlap(f, blocked, written, release, register, deliver, (a, b) -> {
                if (!(a instanceof MilestoneRegistrationOutcome.Registered)) {
                    fail("登録が先なら登録できる: " + a);
                }
            });
            assertThat(f.milestones(number))
                    .extracting(Milestone::state)
                    .containsExactly(MilestoneState.ADOPTED, MilestoneState.DRAFT);
        }
    }
}
