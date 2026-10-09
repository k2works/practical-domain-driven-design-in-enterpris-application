package com.example.cargotracker.tracking.domain.model.aggregates;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.BOOKING_ID;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.CONSIGNEE;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.SHIPPER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.STARTED_AT;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import org.junit.jupiter.api.Test;

/** 追跡記録の開始（ADR-015、T-INV-08・T-INV-10。Bolt 25）。 */
class TrackingRecordTest {

    @Test
    void 予定を採用して追跡を始めると現在状態は集荷予定で予約は確定() {
        TrackingRecord record = start().record();

        assertThat(record.trackingNumber()).isEqualTo(TRACKING_NUMBER);
        assertThat(record.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(record.shipperCompanyId()).isEqualTo(SHIPPER);
        assertThat(record.consigneeCompanyId()).isEqualTo(CONSIGNEE);
        assertThat(record.bookingStatus()).isEqualTo(TrackedBookingStatus.CONFIRMED);
        assertThat(record.currentStatus()).isEqualTo(TrackingStatus.PICKUP_SCHEDULED);
        assertThat(record.schedule()).isEqualTo(schedule());
        assertThat(record.startedAt()).isEqualTo(STARTED_AT);
    }

    @Test
    void 当初の到着予定と最新の見込みは最後の区間の到着予定で始まる() {
        TrackingRecord record = start().record();

        assertThat(record.originalEta()).isEqualTo(at("2026-11-15T00:00:00Z"));
        assertThat(record.latestEta()).isEqualTo(at("2026-11-15T00:00:00Z"));
    }

    @Test
    void 追跡を始めるとDE22を発行する() {
        TrackingStart start = start();

        assertThat(start.event())
                .isEqualTo(new TrackingStarted(
                        TRACKING_NUMBER.value(),
                        BOOKING_ID,
                        STARTED_AT,
                        start.record().aggregateVersion()));
    }

    private static TrackingStart start() {
        return TrackingRecord.start(TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT);
    }
}
