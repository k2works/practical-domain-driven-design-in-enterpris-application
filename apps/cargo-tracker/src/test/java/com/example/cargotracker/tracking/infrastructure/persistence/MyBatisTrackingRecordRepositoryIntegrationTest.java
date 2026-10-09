package com.example.cargotracker.tracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.sql.Timestamp;
import java.time.Instant;
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
