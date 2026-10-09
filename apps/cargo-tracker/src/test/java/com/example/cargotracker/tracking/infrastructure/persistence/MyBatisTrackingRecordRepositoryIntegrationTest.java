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
        TrackingRecord record = started(UUID.randomUUID(), trackingNumber());

        repository.save(record);

        TrackingRecord found = repository.findByBookingId(record.bookingId()).orElseThrow();
        assertThat(found.trackingNumber()).isEqualTo(record.trackingNumber());
        assertThat(found.bookingId()).isEqualTo(record.bookingId());
        assertThat(found.shipperCompanyId()).isEqualTo(record.shipperCompanyId());
        assertThat(found.consigneeCompanyId()).isEqualTo(record.consigneeCompanyId());
        assertThat(found.bookingStatus()).isEqualTo(record.bookingStatus());
        assertThat(found.currentStatus()).isEqualTo(record.currentStatus());
        assertThat(found.schedule()).isEqualTo(record.schedule());
        assertThat(found.originalEta()).isEqualTo(record.originalEta());
        assertThat(found.latestEta()).isEqualTo(record.latestEta());
        assertThat(found.startedAt()).isEqualTo(record.startedAt());
        assertThat(found.aggregateVersion()).isEqualTo(record.aggregateVersion());
        assertThat(jdbc.queryForList(
                        "SELECT leg_no FROM tracking.scheduled_leg WHERE tracking_number = ? ORDER BY leg_no",
                        Integer.class,
                        record.trackingNumber().value()))
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

        assertThatThrownBy(() -> repository.save(started(bookingId, trackingNumber())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 予定区間の到着予定は出発予定より後でなければならない() {
        TrackingRecord record = started(UUID.randomUUID(), trackingNumber());
        repository.save(record);
        Timestamp at = Timestamp.from(Instant.parse("2026-11-20T00:00:00Z"));

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO tracking.scheduled_leg (tracking_number, leg_no, voyage_number, load_unlocode,"
                                + " discharge_unlocode, departure_at, arrival_at) VALUES (?, 3, 'V300', 'USLAX', 'USNYC', ?, ?)",
                        record.trackingNumber().value(),
                        at,
                        at))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 現在状態は決めた値だけ() {
        TrackingRecord record = started(UUID.randomUUID(), trackingNumber());
        repository.save(record);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE tracking.tracking_record SET current_status = 'LOST' WHERE tracking_number = ?",
                        record.trackingNumber().value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 予約状態は決めた値だけ() {
        TrackingRecord record = started(UUID.randomUUID(), trackingNumber());
        repository.save(record);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE tracking.tracking_record SET booking_status = 'AMENDING' WHERE tracking_number = ?",
                        record.trackingNumber().value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static TrackingRecord started(UUID bookingId, TrackingNumber trackingNumber) {
        return TrackingRecord.start(
                        trackingNumber,
                        bookingId,
                        new CompanyId(UUID.randomUUID()),
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        STARTED_AT)
                .record();
    }

    private TrackingNumber trackingNumber() {
        StringBuilder value = new StringBuilder("CT");
        for (int i = 0; i < 12; i++) {
            value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new TrackingNumber(value.toString());
    }
}
