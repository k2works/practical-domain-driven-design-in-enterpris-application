package com.example.cargotracker.tracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentTrackingRecordUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.ExternalMilestoneReceipt;
import com.example.cargotracker.tracking.domain.model.aggregates.MilestoneAdoption;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestone;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestoneRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 追跡記録のリポジトリと tracking スキーマ（PostgreSQL。T-INV-11・T-INV-12。Bolt 25）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisTrackingRecordRepositoryIntegrationTest {

    /** このテストの荷主企業（社内の一覧を荷主企業の集合で読む。Bolt 38g）。 */
    private final CompanyId scenarioShipper = new CompanyId(UUID.randomUUID());

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final UtcInstant STARTED_AT = new UtcInstant(Instant.parse("2026-10-08T09:00:00.123456Z"));
    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-11-01T03:00:00.456789Z"));

    @Autowired
    MyBatisTrackingRecordRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private final Random random = new Random();

    @Test
    void 追跡を開始した追跡記録を予定区間とあわせて保存し予約IDで読み出す() {
        TrackingRecord trackingRecord = started(UUID.randomUUID(), trackingNumber());

        repository.save(trackingRecord);

        TrackingRecord found =
                repository.findByBookingId(trackingRecord.bookingId()).orElseThrow();
        assertThat(found.trackingNumber()).isEqualTo(trackingRecord.trackingNumber());
        assertThat(found.bookingId()).isEqualTo(trackingRecord.bookingId());
        assertThat(found.shipperCompanyId()).isEqualTo(trackingRecord.shipperCompanyId());
        assertThat(found.consigneeCompanyId()).isEqualTo(trackingRecord.consigneeCompanyId());
        assertThat(found.bookingStatus()).isEqualTo(trackingRecord.bookingStatus());
        assertThat(found.currentStatus()).isEqualTo(trackingRecord.currentStatus());
        assertThat(found.schedule()).isEqualTo(trackingRecord.schedule());
        assertThat(found.originalEta()).isEqualTo(trackingRecord.originalEta());
        assertThat(found.latestEta()).isEqualTo(trackingRecord.latestEta());
        assertThat(found.startedAt()).isEqualTo(trackingRecord.startedAt());
        assertThat(found.aggregateVersion()).isEqualTo(trackingRecord.aggregateVersion());
        assertThat(jdbc.queryForList(
                        "SELECT leg_no FROM tracking.scheduled_leg WHERE tracking_number = ? ORDER BY leg_no",
                        Integer.class,
                        trackingRecord.trackingNumber().value()))
                .containsExactly(1, 2);
    }

    @Test
    void ない予約IDは空を返す() {
        assertThat(repository.findByBookingId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void 同じ予約の追跡記録は二件目を保存できない() {
        UUID bookingId = UUID.randomUUID();
        repository.save(started(bookingId, trackingNumber()));
        TrackingRecord second = started(bookingId, trackingNumber());

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 予定区間の到着予定は出発予定より後でなければならない() {
        TrackingRecord trackingRecord = started(UUID.randomUUID(), trackingNumber());
        repository.save(trackingRecord);
        Timestamp at = Timestamp.from(Instant.parse("2026-11-20T00:00:00Z"));
        String trackingNumber = trackingRecord.trackingNumber().value();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO tracking.scheduled_leg (tracking_number, leg_no, voyage_number, load_unlocode,"
                                + " discharge_unlocode, departure_at, arrival_at) VALUES (?, 3, 'V300', 'USLAX', 'USNYC', ?, ?)",
                        trackingNumber,
                        at,
                        at))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 現在状態は決めた値だけ() {
        TrackingRecord trackingRecord = started(UUID.randomUUID(), trackingNumber());
        repository.save(trackingRecord);

        String trackingNumber = trackingRecord.trackingNumber().value();

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE tracking.tracking_record SET current_status = 'LOST' WHERE tracking_number = ?",
                        trackingNumber))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 予約状態は決めた値だけ() {
        TrackingRecord trackingRecord = started(UUID.randomUUID(), trackingNumber());
        repository.save(trackingRecord);

        String trackingNumber = trackingRecord.trackingNumber().value();

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE tracking.tracking_record SET booking_status = 'AMENDING' WHERE tracking_number = ?",
                        trackingNumber))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // S-11・S-12 の照会（Bolt 26）。共有の DB にほかの追跡記録があっても先頭に来るよう、追跡の開始時刻を遠い先にする

    @Test
    void 追跡番号で追跡記録を予定区間とあわせて読み出しない追跡番号は空() {
        TrackingRecord trackingRecord = started(UUID.randomUUID(), trackingNumber());
        repository.save(trackingRecord);

        TrackingRecord found =
                repository.findByTrackingNumber(trackingRecord.trackingNumber()).orElseThrow();

        assertThat(found.bookingId()).isEqualTo(trackingRecord.bookingId());
        assertThat(found.schedule()).isEqualTo(trackingRecord.schedule());
        assertThat(found.startedAt()).isEqualTo(trackingRecord.startedAt());
        assertThat(repository.findByTrackingNumber(trackingNumber())).isEmpty();
    }

    @Test
    void 要約は追跡の開始時刻の新しい順で同じ時刻は追跡番号の順に上限まで返す() {
        TrackingRecord oldest = save("2099-01-01T00:00:00Z", trackingNumber());
        TrackingRecord middle = save("2099-01-02T00:00:00Z", trackingNumber());
        TrackingRecord sameTimeB = save("2099-01-03T00:00:00Z", new TrackingNumber("CTBBBBBBBBBBBB"));
        TrackingRecord sameTimeA = save("2099-01-03T00:00:00Z", new TrackingNumber("CTAAAAAAAAAAAA"));
        // 数字と英字の比較でも、DB の照合順序がメモリの実装（文字コード順。数字が先）と同じ並びになる（Bolt 26 レビュー P-2）
        TrackingRecord sameTimeDigit = save("2099-01-03T00:00:00Z", new TrackingNumber("CT222222222222"));

        assertThat(repository.findRecentSummaries(Set.of(scenarioShipper), 5))
                .extracting(TrackingRecordSummary::trackingNumber)
                .containsExactly(
                        sameTimeDigit.trackingNumber(),
                        sameTimeA.trackingNumber(),
                        sameTimeB.trackingNumber(),
                        middle.trackingNumber(),
                        oldest.trackingNumber());
        assertThat(repository.findRecentSummaries(Set.of(scenarioShipper), 2))
                .extracting(TrackingRecordSummary::trackingNumber)
                .containsExactly(sameTimeDigit.trackingNumber(), sameTimeA.trackingNumber());
        assertThat(repository.findRecentSummaries(Set.of(scenarioShipper), 1).getFirst())
                .isEqualTo(new TrackingRecordSummary(
                        sameTimeDigit.trackingNumber(),
                        TrackingStatus.PICKUP_SCHEDULED,
                        sameTimeDigit.originalEta(),
                        sameTimeDigit.latestEta(),
                        sameTimeDigit.startedAt()));
    }

    // 経路版と到着予定の 4 列は NOT NULL、根拠の実績番号は主要実績を指す（PostgreSQL だけの外部キー。Bolt 26c）

    /** PostgreSQL は制約違反でトランザクションを中断するので、列ごとに 1 つのテストにする。 */
    @ParameterizedTest
    @ValueSource(strings = {"routing_case_number", "route_version_no", "original_eta", "latest_eta"})
    void 経路版と到着予定の列はNULLを入れられない(String column) {
        TrackingRecord trackingRecord = started(UUID.randomUUID(), trackingNumber());
        repository.save(trackingRecord);
        String trackingNumber = trackingRecord.trackingNumber().value();
        // 経路版の 2 列は、2 列そろいの CHECK に当たらないよう両方を NULL にする（NOT NULL で拒否することを確かめる）
        String assignment =
                column.startsWith("rout") ? "routing_case_number = NULL, route_version_no = NULL" : column + " = NULL";

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE tracking.tracking_record SET " + assignment + " WHERE tracking_number = ?",
                        trackingNumber))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(column.startsWith("rout") ? "routing_case_number" : column);
    }

    @Test
    void 根拠の実績番号はない主要実績を指せない() {
        TrackingRecord trackingRecord = started(UUID.randomUUID(), trackingNumber());
        repository.save(trackingRecord);
        String trackingNumber = trackingRecord.trackingNumber().value();
        jdbc.update(
                "UPDATE tracking.tracking_record SET status_basis_milestone_no = 9 WHERE tracking_number = ?",
                trackingNumber);

        // 外部キーはコミットのときに検査する（DEFERRABLE）ので、テストのトランザクションの中で直ちに検査させる
        assertThatThrownBy(() -> jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_tracking_record_status_basis");
    }

    @Test
    void 根拠の実績番号は登録した主要実績を指せる() {
        TrackingRecord trackingRecord = started(UUID.randomUUID(), trackingNumber());
        repository.save(trackingRecord);
        repository.update(register(trackingRecord, MilestoneKind.PICKUP, "2026-11-01T02:30:00Z", "F-118"));

        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");

        assertThat(repository
                        .findByTrackingNumber(trackingRecord.trackingNumber())
                        .orElseThrow()
                        .statusBasisMilestoneNo())
                .isEqualTo(OptionalInt.of(1));
    }

    // 荷主の照会（C-10。BR-07。Bolt 27）

    @Test
    void 追跡番号と荷主企業で引くと自社の追跡記録だけを主要実績とあわせて読み出し他社は空() {
        TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
        repository.save(saved);
        repository.update(register(saved, MilestoneKind.PICKUP, "2026-11-01T02:30:00Z", "F-118"));

        TrackingRecord found = repository
                .findByTrackingNumber(saved.trackingNumber(), saved.shipperCompanyId())
                .orElseThrow();
        assertThat(found.milestones()).hasSize(1);
        assertThat(found.schedule()).isEqualTo(saved.schedule());
        assertThat(repository.findByTrackingNumber(saved.trackingNumber(), new CompanyId(UUID.randomUUID())))
                .isEmpty();
    }

    @Test
    void 荷主企業の要約は自社の追跡記録だけを新しい順に上限まで返し最新の到着見込みを持つ() {
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        TrackingRecord older = saveFor(shipper, "2099-01-01T00:00:00Z");
        TrackingRecord newer = saveFor(shipper, "2099-01-02T00:00:00Z");
        saveFor(new CompanyId(UUID.randomUUID()), "2099-01-03T00:00:00Z");

        assertThat(repository.findRecentSummariesByShipper(shipper, 10))
                .extracting(TrackingRecordSummary::trackingNumber)
                .containsExactly(newer.trackingNumber(), older.trackingNumber());
        assertThat(repository.findRecentSummariesByShipper(shipper, 1)).hasSize(1);
        assertThat(repository
                        .findRecentSummariesByShipper(shipper, 1)
                        .getFirst()
                        .latestEta())
                .isEqualTo(newer.latestEta());
    }

    @Test
    void 社内の要約は荷主企業の集合の追跡記録だけを返し空の集合なら何も返さない() {
        // 行 1c・1d（社内の一覧は担当企業の集合で絞る。Bolt 38g）
        CompanyId other = new CompanyId(UUID.randomUUID());
        TrackingRecord ofShipper = save("2099-01-01T00:00:00Z", trackingNumber());
        TrackingRecord ofOther = saveFor(other, "2099-01-02T00:00:00Z");
        TrackingRecord ofThird = saveFor(new CompanyId(UUID.randomUUID()), "2099-01-03T00:00:00Z");

        assertThat(repository.findRecentSummaries(Set.of(scenarioShipper, other), 10))
                .extracting(TrackingRecordSummary::trackingNumber)
                .containsExactly(ofOther.trackingNumber(), ofShipper.trackingNumber())
                .doesNotContain(ofThird.trackingNumber());
        assertThat(repository.findRecentSummaries(Set.of(), 10)).isEmpty();
    }

    @Test
    void 荷受人の企業で荷主の口を引いても追跡記録は出ない() {
        // 荷主の口は荷主企業だけで絞る。荷受人の照会（US-10）は開示範囲が違うので別の口で作る（BR-07。Bolt 27 の開発レビュー）
        TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
        repository.save(saved);

        assertThat(repository.findByTrackingNumber(saved.trackingNumber(), saved.consigneeCompanyId()))
                .isEmpty();
        assertThat(repository.findRecentSummariesByShipper(saved.consigneeCompanyId(), 10))
                .isEmpty();
    }

    @Test
    void 荷主の一覧の索引がある() {
        assertThat(jdbc.queryForList(
                        "SELECT indexdef FROM pg_indexes WHERE schemaname = 'tracking' AND indexname = 'ix_tracking_record_shipper'",
                        String.class))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("(shipper_company_id, created_at)");
    }

    private TrackingRecord saveFor(CompanyId shipper, String startedAt) {
        TrackingRecord trackingRecord = TrackingRecord.start(
                        trackingNumber(),
                        UUID.randomUUID(),
                        shipper,
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        new UtcInstant(Instant.parse(startedAt)))
                .trackingRecord();
        repository.save(trackingRecord);
        return trackingRecord;
    }

    // 主要実績（US-12 AC1・AC2、T-INV-01・T-INV-02。Bolt 26b）

    @Test
    void 主要実績を登録した追跡記録を更新すると実績を追加し現在状態と根拠の実績番号と版を書き直す() {
        TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
        repository.save(saved);
        TrackingRecord registered = register(saved, MilestoneKind.PICKUP, "2026-11-01T02:30:00.654321Z", "F-118");

        repository.update(registered);

        TrackingRecord found =
                repository.findByTrackingNumber(saved.trackingNumber()).orElseThrow();
        assertThat(found.milestones()).isEqualTo(registered.milestones());
        assertThat(found.currentStatus()).isEqualTo(TrackingStatus.PICKED_UP);
        assertThat(found.statusBasisMilestoneNo()).isEqualTo(OptionalInt.of(1));
        assertThat(found.aggregateVersion()).isEqualTo(saved.aggregateVersion() + 1);
        assertThat(repository.findByBookingId(saved.bookingId()).orElseThrow().milestones())
                .isEqualTo(registered.milestones());
    }

    @Test
    void 読み直して2件目の実績を登録すると実績番号の順に2件を読み出す() {
        TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
        repository.save(saved);
        repository.update(register(saved, MilestoneKind.PICKUP, "2026-11-01T02:30:00Z", "F-118"));
        TrackingRecord reloaded =
                repository.findByTrackingNumber(saved.trackingNumber()).orElseThrow();

        repository.update(register(reloaded, MilestoneKind.RECEIPT_AT_ORIGIN, "2026-11-01T02:50:00Z", "F-119"));

        TrackingRecord found =
                repository.findByTrackingNumber(saved.trackingNumber()).orElseThrow();
        assertThat(found.milestones()).extracting(Milestone::milestoneNo).containsExactly(1, 2);
        assertThat(found.currentStatus()).isEqualTo(TrackingStatus.RECEIVED_AT_ORIGIN);
        assertThat(found.statusBasisMilestoneNo()).isEqualTo(OptionalInt.of(2));
        assertThat(found.aggregateVersion()).isEqualTo(2);
    }

    @Test
    void 読み込んだ後に別の更新が先に保存されていたら競合にして実績を追加しない() {
        TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
        repository.save(saved);
        repository.update(register(saved, MilestoneKind.PICKUP, "2026-11-01T02:30:00Z", "F-118"));
        TrackingRecord stale = register(saved, MilestoneKind.DEPARTURE, "2026-11-01T02:40:00Z", "F-120");

        assertThatThrownBy(() -> repository.update(stale)).isInstanceOf(ConcurrentTrackingRecordUpdateException.class);
        assertThat(repository
                        .findByTrackingNumber(saved.trackingNumber())
                        .orElseThrow()
                        .milestones())
                .extracting(milestone -> milestone.source().reference())
                .containsExactly("F-118");
    }

    @Test
    void 同じ出典の実績が同時に登録されていたら一意制約の違反を競合にする() {
        TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
        repository.save(saved);
        // 別の登録が先に同じ出典の実績を書いた（版はまだ増えていない瞬間を表に直接作る）
        jdbc.update(
                "INSERT INTO tracking.milestone (tracking_number, milestone_no, kind, location_unlocode, occurred_at,"
                        + " source_kind, source_ref, acquired_at, state, registered_by, registered_at)"
                        + " VALUES (?, 9, 'PICKUP', 'JPTYO', ?, 'FIELD_RECORD', 'F-118', ?, 'ADOPTED', ?, ?)",
                saved.trackingNumber().value(),
                Timestamp.from(Instant.parse("2026-11-01T02:30:00Z")),
                Timestamp.from(NOW.instant()),
                UUID.randomUUID(),
                Timestamp.from(NOW.instant()));

        TrackingRecord registered = register(saved, MilestoneKind.PICKUP, "2026-11-01T02:30:00Z", "F-118");

        assertThatThrownBy(() -> repository.update(registered))
                .isInstanceOf(ConcurrentTrackingRecordUpdateException.class);
        // 先に書いた追跡記録の UPDATE も、セーブポイントに戻って元のまま（版・現在状態・根拠の実績番号）
        assertThat(jdbc.queryForMap(
                        "SELECT version, current_status, status_basis_milestone_no FROM tracking.tracking_record"
                                + " WHERE tracking_number = ?",
                        saved.trackingNumber().value()))
                .containsEntry("version", 0L)
                .containsEntry("current_status", "PICKUP_SCHEDULED")
                .containsEntry("status_basis_milestone_no", null);
    }

    // PostgreSQL は制約違反でトランザクションを中断するので、制約ごとに 1 つのテストにする

    @Test
    void 主要実績の種類は決めた値だけ() {
        assertMilestoneConstraint("kind = 'LOADING'");
    }

    @Test
    void 主要実績の出典の種類は決めた値だけ() {
        assertMilestoneConstraint("source_kind = 'RUMOR'");
    }

    @Test
    void 主要実績の状態は決めた値だけ() {
        assertMilestoneConstraint("state = 'DELETED'");
    }

    /** DE-13 の実績の外部の識別と拒否の記録（Bolt 39g の行 25〜27）。クラスの @Transactional で戻す。 */
    @Nested
    class Bolt39g_外部の識別と拒否の記録 {

        @Autowired
        RejectedMilestoneRepository rejectedMilestones;

        private static final UUID DATA_SOURCE = UUID.fromString("00000000-0000-0000-0000-000000003921");
        private static final UtcInstant PUBLISHED_AT = new UtcInstant(Instant.parse("2026-11-19T15:00:00.123456Z"));

        private ExternalMilestone external(TrackingNumber number, String receivedRecordId) {
            return new ExternalMilestone(
                    number,
                    MilestoneKind.DEPARTURE,
                    new Location("JPTYO"),
                    new UtcInstant(Instant.parse("2026-11-19T14:00:00Z")),
                    new Source(SourceKind.EXTERNAL_RECORD, receivedRecordId, NOW),
                    new ExternalOrigin(DATA_SOURCE, "MS-0001", PUBLISHED_AT),
                    new UserId(UUID.fromString("00000000-0000-0000-0000-000000003922")));
        }

        @Test
        void 行25_外部の3列が往復し手入力の実績は3列ともNULL() {
            TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
            repository.save(saved);
            TrackingRecord withManual = register(saved, MilestoneKind.PICKUP, "2026-11-01T02:30:00Z", "F-118");
            repository.update(withManual);
            TrackingRecord reloaded =
                    repository.findByTrackingNumber(saved.trackingNumber()).orElseThrow();
            ExternalMilestoneReceipt.Received received =
                    (ExternalMilestoneReceipt.Received) reloaded.receiveExternalMilestone(
                            external(saved.trackingNumber(), UUID.randomUUID().toString()), NOW);

            repository.update(received.trackingRecord());

            List<Milestone> found = repository
                    .findByTrackingNumber(saved.trackingNumber())
                    .orElseThrow()
                    .milestones();
            assertThat(found).hasSize(2);
            assertThat(found.get(0).externalOrigin()).isNull();
            assertThat(found.get(1).externalOrigin())
                    .isEqualTo(new ExternalOrigin(DATA_SOURCE, "MS-0001", PUBLISHED_AT));
            assertThat(found.get(1)).isEqualTo(received.milestone());
            assertThat(jdbc.queryForMap(
                            "SELECT data_source_id, external_id, published_at FROM tracking.milestone"
                                    + " WHERE tracking_number = ? AND milestone_no = 1",
                            saved.trackingNumber().value()))
                    .containsEntry("data_source_id", null)
                    .containsEntry("external_id", null)
                    .containsEntry("published_at", null);
        }

        private int insertMilestone(String sourceKind, UUID dataSourceId, String externalId, Timestamp publishedAt) {
            TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
            repository.save(saved);
            Timestamp at = Timestamp.from(Instant.parse("2026-11-19T14:00:00Z"));
            return jdbc.update(
                    "INSERT INTO tracking.milestone (tracking_number, milestone_no, kind, location_unlocode, occurred_at,"
                            + " source_kind, source_ref, acquired_at, state, registered_by, registered_at,"
                            + " data_source_id, external_id, published_at)"
                            + " VALUES (?, 1, 'DEPARTURE', 'JPTYO', ?, ?, 'R-1', ?, 'DRAFT', ?, ?, ?, ?, ?)",
                    saved.trackingNumber().value(),
                    at,
                    sourceKind,
                    at,
                    UUID.randomUUID(),
                    at,
                    dataSourceId,
                    externalId,
                    publishedAt);
        }

        private static final Timestamp PUBLISHED = Timestamp.from(Instant.parse("2026-11-19T15:00:00Z"));

        static Stream<Arguments> inconsistentExternalColumns() {
            UUID id = UUID.randomUUID();
            return Stream.of(
                    Arguments.of("外部原本で情報源 ID がない", "EXTERNAL_RECORD", null, "MS-1", PUBLISHED),
                    Arguments.of("外部原本で外部 ID がない", "EXTERNAL_RECORD", id, null, PUBLISHED),
                    Arguments.of("外部原本で公開した時刻がない", "EXTERNAL_RECORD", id, "MS-1", null),
                    Arguments.of("外部原本で 3 列ともない", "EXTERNAL_RECORD", null, null, null),
                    Arguments.of("現場記録で情報源 ID がある", "FIELD_RECORD", id, null, null),
                    Arguments.of("社内確認で外部 ID がある", "INTERNAL_CHECK", null, "MS-1", null),
                    Arguments.of("手動入力で公開した時刻がある", "MANUAL_ENTRY", null, null, PUBLISHED),
                    Arguments.of("現場記録で 3 列ともある", "FIELD_RECORD", id, "MS-1", PUBLISHED));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("inconsistentExternalColumns")
        void 行26_出典の種類と外部の3列がそろわない行はCHECKで拒否する(
                String description, String sourceKind, UUID dataSourceId, String externalId, Timestamp publishedAt) {
            assertThatThrownBy(() -> insertMilestone(sourceKind, dataSourceId, externalId, publishedAt))
                    .as(description)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_milestone_external_origin");
        }

        @Test
        void 行26_外部原本で3列がそろう行は入る() {
            assertThat(insertMilestone("EXTERNAL_RECORD", UUID.randomUUID(), "MS-1", PUBLISHED))
                    .isEqualTo(1);
        }

        @Test
        void 行26_手入力の出典で3列ともNULLの行は入る() {
            assertThat(insertMilestone("FIELD_RECORD", null, null, null)).isEqualTo(1);
        }

        private RejectedMilestone rejected(String reference, MilestoneRejectionReason reason) {
            return RejectedMilestone.of(UUID.randomUUID(), external(trackingNumber(), reference), reason, NOW);
        }

        @Test
        void 行27_拒否の記録を書くと値が往復し同じ出典の2度目は増えない() {
            String reference = UUID.randomUUID().toString();
            RejectedMilestone first = rejected(reference, MilestoneRejectionReason.UNKNOWN_TRACKING_NUMBER);

            rejectedMilestones.saveIfAbsent(first);
            rejectedMilestones.saveIfAbsent(rejected(reference, MilestoneRejectionReason.BOOKING_CANCELLED));

            assertThat(rejectedMilestones.existsBySource(first.source())).isTrue();
            assertThat(jdbc.queryForMap(
                            "SELECT id, tracking_number, kind, location_unlocode, source_kind, data_source_id, external_id,"
                                    + " reason FROM tracking.rejected_milestone WHERE source_ref = ?",
                            reference))
                    .containsEntry("id", first.id())
                    .containsEntry("tracking_number", first.trackingNumber().value())
                    .containsEntry("kind", "DEPARTURE")
                    .containsEntry("location_unlocode", "JPTYO")
                    .containsEntry("source_kind", "EXTERNAL_RECORD")
                    .containsEntry("data_source_id", DATA_SOURCE)
                    .containsEntry("external_id", "MS-0001")
                    .containsEntry("reason", "UNKNOWN_TRACKING_NUMBER");
        }

        @Test
        void 行27_出典が違えば別の拒否の記録になり種類が違う出典は別と判定する() {
            String reference = UUID.randomUUID().toString();
            rejectedMilestones.saveIfAbsent(rejected(reference, MilestoneRejectionReason.UNKNOWN_TRACKING_NUMBER));

            assertThat(rejectedMilestones.existsBySource(new Source(SourceKind.EXTERNAL_RECORD, reference, NOW)))
                    .isTrue();
            assertThat(rejectedMilestones.existsBySource(new Source(SourceKind.FIELD_RECORD, reference, NOW)))
                    .isFalse();
            assertThat(rejectedMilestones.existsBySource(new Source(
                            SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), NOW)))
                    .isFalse();
        }

        @Test
        void 行26_拒否の理由は決めた値だけ() {
            Timestamp at = Timestamp.from(Instant.parse("2026-11-19T14:00:00Z"));
            UUID id = UUID.randomUUID();
            String reference = UUID.randomUUID().toString();
            UUID dataSource = UUID.randomUUID();

            assertThatThrownBy(() -> jdbc.update(
                            "INSERT INTO tracking.rejected_milestone (id, tracking_number, kind, location_unlocode,"
                                    + " occurred_at, source_kind, source_ref, acquired_at, data_source_id, external_id,"
                                    + " reason, recorded_at)"
                                    + " VALUES (?, 'CTZZZZZZZZZZZZ', 'DEPARTURE', 'JPTYO', ?, 'EXTERNAL_RECORD', ?, ?, ?,"
                                    + " 'MS-1', 'OCCURRED_IN_FUTURE', ?)",
                            id,
                            at,
                            reference,
                            at,
                            dataSource,
                            at))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_rejected_milestone_reason");
        }

        @Test
        void 行26_拒否の記録は同じ出典の種類と参照を一意にする() {
            Timestamp at = Timestamp.from(Instant.parse("2026-11-19T14:00:00Z"));
            String reference = UUID.randomUUID().toString();
            String insert = "INSERT INTO tracking.rejected_milestone (id, tracking_number, kind, location_unlocode,"
                    + " occurred_at, source_kind, source_ref, acquired_at, data_source_id, external_id,"
                    + " reason, recorded_at)"
                    + " VALUES (?, 'CTZZZZZZZZZZZZ', 'DEPARTURE', 'JPTYO', ?, 'EXTERNAL_RECORD', ?, ?, ?,"
                    + " 'MS-1', 'BOOKING_CANCELLED', ?)";
            jdbc.update(insert, UUID.randomUUID(), at, reference, at, UUID.randomUUID(), at);

            UUID secondId = UUID.randomUUID();
            UUID dataSource = UUID.randomUUID();

            assertThatThrownBy(() -> jdbc.update(insert, secondId, at, reference, at, dataSource, at))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_rejected_milestone_source");
        }
    }

    /** 下書きの採用の保存と P-5・P-6（Bolt 39gb の行 50・57・59〜61）。クラスの @Transactional で戻す。 */
    @Nested
    class Bolt39gb_下書きの採用 {

        private static final UUID DATA_SOURCE = UUID.fromString("00000000-0000-0000-0000-0000039b0e01");
        private static final UserId MANAGER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000039b0e02"));
        private static final UtcInstant ADOPTED_AT = new UtcInstant(Instant.parse("2026-11-20T00:00:00.123456Z"));

        private TrackingRecord withDraft() {
            TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
            repository.save(saved);
            ExternalMilestone external = new ExternalMilestone(
                    saved.trackingNumber(),
                    MilestoneKind.DEPARTURE,
                    new Location("JPTYO"),
                    new UtcInstant(Instant.parse("2026-11-19T14:00:00Z")),
                    new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), NOW),
                    new ExternalOrigin(DATA_SOURCE, "MS-0001", NOW),
                    MANAGER);
            repository.update(((ExternalMilestoneReceipt.Received) saved.receiveExternalMilestone(external, NOW))
                    .trackingRecord());
            return repository.findByTrackingNumber(saved.trackingNumber()).orElseThrow();
        }

        @Test
        void 行50_採用を保存すると状態と採用者と採用時刻が往復し現在状態と版を書き直す() {
            TrackingRecord draft = withDraft();
            MilestoneAdoption.Adopted adopted =
                    (MilestoneAdoption.Adopted) draft.adoptCandidate(1, MANAGER, ADOPTED_AT);

            repository.update(adopted.trackingRecord());

            TrackingRecord found = repository.findByTrackingNumber(draft.trackingNumber()).orElseThrow();
            assertThat(found.milestones()).containsExactly(adopted.milestone());
            assertThat(found.currentStatus()).isEqualTo(TrackingStatus.IN_TRANSIT);
            assertThat(found.statusBasisMilestoneNo()).isEqualTo(OptionalInt.of(1));
            assertThat(found.aggregateVersion()).isEqualTo(draft.aggregateVersion() + 1);
        }

        @Test
        void 行57_下書きの行がもう下書きでなければ競合にして戻す() {
            TrackingRecord draft = withDraft();
            // 別の採用が先に同じ行を採用にした（版はまだ増えていない瞬間を表に直接作る）
            jdbc.update(
                    "UPDATE tracking.milestone SET state = 'ADOPTED', adopted_by = ?, adopted_at = ?"
                            + " WHERE tracking_number = ? AND milestone_no = 1",
                    UUID.randomUUID(),
                    Timestamp.from(NOW.instant()),
                    draft.trackingNumber().value());
            TrackingRecord adopted =
                    ((MilestoneAdoption.Adopted) draft.adoptCandidate(1, MANAGER, ADOPTED_AT)).trackingRecord();

            assertThatThrownBy(() -> repository.update(adopted))
                    .isInstanceOf(ConcurrentTrackingRecordUpdateException.class);
            assertThat(jdbc.queryForMap(
                            "SELECT version, current_status FROM tracking.tracking_record WHERE tracking_number = ?",
                            draft.trackingNumber().value()))
                    .containsEntry("version", draft.aggregateVersion())
                    .containsEntry("current_status", "PICKUP_SCHEDULED");
        }

        private int insertMilestone(String sourceKind, String state, UUID adoptedBy, Timestamp adoptedAt) {
            TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
            repository.save(saved);
            Timestamp at = Timestamp.from(Instant.parse("2026-11-19T14:00:00Z"));
            boolean external = "EXTERNAL_RECORD".equals(sourceKind);
            return jdbc.update(
                    "INSERT INTO tracking.milestone (tracking_number, milestone_no, kind, location_unlocode, occurred_at,"
                            + " source_kind, source_ref, acquired_at, state, registered_by, registered_at,"
                            + " data_source_id, external_id, published_at, adopted_by, adopted_at)"
                            + " VALUES (?, 1, 'DEPARTURE', 'JPTYO', ?, ?, 'R-1', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    saved.trackingNumber().value(),
                    at,
                    sourceKind,
                    at,
                    state,
                    UUID.randomUUID(),
                    at,
                    external ? DATA_SOURCE : null,
                    external ? "MS-1" : null,
                    external ? at : null,
                    adoptedBy,
                    adoptedAt);
        }

        private static final Timestamp AT = Timestamp.from(Instant.parse("2026-11-20T00:00:00Z"));

        static Stream<Arguments> inconsistentAdoption() {
            UUID by = UUID.randomUUID();
            return Stream.of(
                    Arguments.of("外部原本の採用で採用者がない", "EXTERNAL_RECORD", "ADOPTED", null, AT),
                    Arguments.of("外部原本の採用で採用時刻がない", "EXTERNAL_RECORD", "ADOPTED", by, null),
                    Arguments.of("外部原本の確認中で 2 列ともない", "EXTERNAL_RECORD", "UNDER_REVIEW", null, null),
                    Arguments.of("外部原本の下書きで採用者がある", "EXTERNAL_RECORD", "DRAFT", by, AT),
                    Arguments.of("外部原本の保持のみで採用時刻がある", "EXTERNAL_RECORD", "RETAINED_ONLY", null, AT),
                    Arguments.of("手入力の採用で 2 列ともある", "FIELD_RECORD", "ADOPTED", by, AT));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("inconsistentAdoption")
        void 行59_採用者と採用時刻がそろわない行はCHECKで拒否する(
                String description, String sourceKind, String state, UUID adoptedBy, Timestamp adoptedAt) {
            assertThatThrownBy(() -> insertMilestone(sourceKind, state, adoptedBy, adoptedAt))
                    .as(description)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_milestone_adoption");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "外部原本の採用, EXTERNAL_RECORD, ADOPTED, true",
            "外部原本の確認中, EXTERNAL_RECORD, UNDER_REVIEW, true",
            "外部原本の下書き, EXTERNAL_RECORD, DRAFT, false",
            "手入力の採用, FIELD_RECORD, ADOPTED, false"
        })
        void 行59_そろう行は入る(String description, String sourceKind, String state, boolean withAdoption) {
            assertThat(insertMilestone(
                            sourceKind, state, withAdoption ? UUID.randomUUID() : null, withAdoption ? AT : null))
                    .isEqualTo(1);
        }

        @Test
        void 行60_根拠の実績番号の実績がなければ500にせず競合にする() {
            TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
            repository.save(saved);
            // 集約の不変条件が破れた追跡記録（根拠の実績番号の実績がない）
            TrackingRecord broken = TrackingRecord.reconstitute(
                    saved.trackingNumber(),
                    saved.bookingId(),
                    saved.shipperCompanyId(),
                    saved.consigneeCompanyId(),
                    saved.bookingStatus(),
                    saved.schedule(),
                    TrackingStatus.PICKED_UP,
                    OptionalInt.of(5),
                    List.of(),
                    saved.originalEta(),
                    saved.latestEta(),
                    saved.startedAt(),
                    saved.aggregateVersion());

            assertThatThrownBy(() -> repository.update(broken))
                    .isInstanceOf(ConcurrentTrackingRecordUpdateException.class);
            assertThat(jdbc.queryForMap(
                            "SELECT version, status_basis_milestone_no FROM tracking.tracking_record"
                                    + " WHERE tracking_number = ?",
                            saved.trackingNumber().value()))
                    .containsEntry("version", 0L)
                    .containsEntry("status_basis_milestone_no", null);
        }
    }

    private void assertMilestoneConstraint(String assignment) {
        TrackingRecord saved = started(UUID.randomUUID(), trackingNumber());
        repository.save(saved);
        repository.update(register(saved, MilestoneKind.PICKUP, "2026-11-01T02:30:00Z", "F-118"));
        String trackingNumber = saved.trackingNumber().value();

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE tracking.milestone SET " + assignment + " WHERE tracking_number = ?", trackingNumber))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static TrackingRecord register(
            TrackingRecord trackingRecord, MilestoneKind kind, String occurredAt, String reference) {
        return trackingRecord
                .registerMilestone(
                        kind,
                        new Location("JPTYO"),
                        new UtcInstant(Instant.parse(occurredAt)),
                        new Source(SourceKind.FIELD_RECORD, reference, NOW),
                        new UserId(UUID.fromString("00000000-0000-0000-0000-000000026b01")),
                        NOW)
                .trackingRecord();
    }

    private TrackingRecord save(String startedAt, TrackingNumber trackingNumber) {
        TrackingRecord trackingRecord = TrackingRecord.start(
                        trackingNumber,
                        UUID.randomUUID(),
                        scenarioShipper,
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        new UtcInstant(Instant.parse(startedAt)))
                .trackingRecord();
        repository.save(trackingRecord);
        return trackingRecord;
    }

    private static TrackingRecord started(UUID bookingId, TrackingNumber trackingNumber) {
        return TrackingRecord.start(
                        trackingNumber,
                        bookingId,
                        new CompanyId(UUID.randomUUID()),
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        STARTED_AT)
                .trackingRecord();
    }

    private TrackingNumber trackingNumber() {
        StringBuilder value = new StringBuilder("CT");
        for (int i = 0; i < 12; i++) {
            value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new TrackingNumber(value.toString());
    }
}
