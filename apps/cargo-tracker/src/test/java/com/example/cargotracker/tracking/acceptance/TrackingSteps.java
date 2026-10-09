package com.example.cargotracker.tracking.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import io.cucumber.java.ja.ならば;
import java.time.Instant;
import java.util.Arrays;

/** 追跡の開始の受入シナリオのステップ（ADR-015、T-INV-10〜12。Bolt 25）。 */
public class TrackingSteps {

    private final InMemoryTrackingRecordRepository trackingRecords;

    public TrackingSteps(InMemoryTrackingRecordRepository trackingRecords) {
        this.trackingRecords = trackingRecords;
    }

    @ならば("本予約の追跡記録が {int} 件でき、予定は経路版 {string} 版 {int} の区間 {string} である")
    public void 追跡記録ができる(int count, String routingCaseNumber, int routeVersionNo, String voyages) {
        assertThat(trackingRecords.all()).hasSize(count);
        TrackingRecord record = trackingRecords.all().getFirst();
        assertThat(record.schedule().routingCaseNumber()).isEqualTo(routingCaseNumber);
        assertThat(record.schedule().routeVersionNo()).isEqualTo(routeVersionNo);
        assertThat(record.schedule().legs())
                .extracting(ScheduledLeg::voyageNumber)
                .containsExactlyElementsOf(Arrays.asList(voyages.split(",")));
    }

    @ならば("追跡の現在状態は {string} で、当初の到着予定と最新の見込みは {string} である")
    public void 現在状態と到着予定(String status, String eta) {
        TrackingRecord record = trackingRecords.all().getFirst();
        assertThat(record.currentStatus()).isEqualTo(status(status));
        UtcInstant expected = new UtcInstant(Instant.parse(eta));
        assertThat(record.originalEta()).isEqualTo(expected);
        assertThat(record.latestEta()).isEqualTo(expected);
    }

    @ならば("本予約の追跡記録は {int} 件だけある")
    public void 追跡記録の件数(int count) {
        assertThat(trackingRecords.all()).hasSize(count);
    }

    private static TrackingStatus status(String name) {
        return switch (name) {
            case "集荷予定" -> TrackingStatus.PICKUP_SCHEDULED;
            default -> throw new IllegalArgumentException("未知の追跡状態: " + name);
        };
    }
}
