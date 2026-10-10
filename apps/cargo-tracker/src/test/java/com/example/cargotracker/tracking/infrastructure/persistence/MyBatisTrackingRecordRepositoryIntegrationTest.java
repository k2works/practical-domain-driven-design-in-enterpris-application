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
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

    /**
     * 表では経路版と到着予定の列が NULL 可（data_model.md）だが、追跡記録は予定と到着予定を必ず持つ。NULL の行は、原因の分かる例外に
     * する（自動の unboxing の NPE にしない。Bolt 25 レビュー P-9・A-9）。
     */
    @Test
    void 予定の経路版のない行は原因の分かる例外にする() {
        UUID bookingId = UUID.randomUUID();
        String trackingNumber = trackingNumber().value();
        jdbc.update(
                "INSERT INTO tracking.tracking_record (tracking_number, booking_id, shipper_company_id,"
                        + " consignee_company_id, booking_status, current_status, version, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'CONFIRMED', 'PICKUP_SCHEDULED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                trackingNumber,
                bookingId,
                UUID.randomUUID(),
                UUID.randomUUID());

        assertThatThrownBy(() -> repository.findByBookingId(bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(trackingNumber);
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

        assertThat(repository.findRecentSummaries(5))
                .extracting(TrackingRecordSummary::trackingNumber)
                .containsExactly(
                        sameTimeDigit.trackingNumber(),
                        sameTimeA.trackingNumber(),
                        sameTimeB.trackingNumber(),
                        middle.trackingNumber(),
                        oldest.trackingNumber());
        assertThat(repository.findRecentSummaries(2))
                .extracting(TrackingRecordSummary::trackingNumber)
                .containsExactly(sameTimeDigit.trackingNumber(), sameTimeA.trackingNumber());
        assertThat(repository.findRecentSummaries(1).getFirst())
                .isEqualTo(new TrackingRecordSummary(
                        sameTimeDigit.trackingNumber(),
                        TrackingStatus.PICKUP_SCHEDULED,
                        sameTimeDigit.originalEta(),
                        sameTimeDigit.startedAt()));
    }

    /** 要約でも、当初の到着予定のない行は原因の分かる例外にする（集約の組み立てと同じ。Bolt 26 計画の確認ポイント 13）。 */
    @Test
    void 当初の到着予定のない行の要約は原因の分かる例外にする() {
        String trackingNumber = trackingNumber().value();
        jdbc.update(
                "INSERT INTO tracking.tracking_record (tracking_number, booking_id, shipper_company_id,"
                        + " consignee_company_id, booking_status, current_status, version, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'CONFIRMED', 'PICKUP_SCHEDULED', 0, ?, ?)",
                trackingNumber,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Timestamp.from(Instant.parse("2099-12-31T00:00:00Z")),
                Timestamp.from(Instant.parse("2099-12-31T00:00:00Z")));

        assertThatThrownBy(() -> repository.findRecentSummaries(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(trackingNumber);
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
                        new CompanyId(UUID.randomUUID()),
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
