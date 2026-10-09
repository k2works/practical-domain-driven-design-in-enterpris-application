package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.annotation.ddd.CoreConcept;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.Objects;
import java.util.UUID;

/**
 * 追跡記録。1 つの追跡番号の予定と主要実績、現在状態を持つ（集約ルート。ADR-002・015。Bolt 25）。予約 1 件に 1 件（T-INV-11）。
 * 本予約の確定（DE-07）を受けて、確定した経路版の区間を予定として採用して始める。現在状態は予定を採用した直後の集荷予定で始め（T-INV-08 の
 * 例外）、当初の到着予定と最新の見込みは最後の区間の到着予定で始める（T-INV-10）。主要実績と現在状態の導出は US-12（Bolt 26）で足す。
 */
@AggregateRoot
@CoreConcept
public final class TrackingRecord {

    private static final long INITIAL_AGGREGATE_VERSION = 0;

    private final TrackingNumber trackingNumber;
    private final UUID bookingId;
    private final CompanyId shipperCompanyId;
    private final CompanyId consigneeCompanyId;
    private final TrackedBookingStatus bookingStatus;
    private final Schedule schedule;
    private final TrackingStatus currentStatus;
    private final UtcInstant originalEta;
    private final UtcInstant latestEta;
    private final UtcInstant startedAt;
    private final long aggregateVersion;

    private TrackingRecord(
            TrackingNumber trackingNumber,
            UUID bookingId,
            CompanyId shipperCompanyId,
            CompanyId consigneeCompanyId,
            TrackedBookingStatus bookingStatus,
            Schedule schedule,
            TrackingStatus currentStatus,
            UtcInstant originalEta,
            UtcInstant latestEta,
            UtcInstant startedAt,
            long aggregateVersion) {
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.shipperCompanyId = Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        this.consigneeCompanyId = Objects.requireNonNull(consigneeCompanyId, "consigneeCompanyId");
        this.bookingStatus = Objects.requireNonNull(bookingStatus, "bookingStatus");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.currentStatus = Objects.requireNonNull(currentStatus, "currentStatus");
        this.originalEta = Objects.requireNonNull(originalEta, "originalEta");
        this.latestEta = Objects.requireNonNull(latestEta, "latestEta");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 予定を採用して追跡を始める（ADR-015）。
     *
     * @param trackingNumber 予約が発行した追跡番号
     * @param bookingId 予約 ID
     * @param shipperCompanyId 荷主企業 ID
     * @param consigneeCompanyId 荷受人企業 ID
     * @param schedule 確定した経路版の区間の予定
     * @param startedAt 開始時刻
     * @return 追跡記録と DE-22
     */
    public static TrackingStart start(
            TrackingNumber trackingNumber,
            UUID bookingId,
            CompanyId shipperCompanyId,
            CompanyId consigneeCompanyId,
            Schedule schedule,
            UtcInstant startedAt) {
        Objects.requireNonNull(schedule, "schedule");
        TrackingRecord record = new TrackingRecord(
                trackingNumber,
                bookingId,
                shipperCompanyId,
                consigneeCompanyId,
                TrackedBookingStatus.CONFIRMED,
                schedule,
                TrackingStatus.PICKUP_SCHEDULED,
                schedule.finalArrival(),
                schedule.finalArrival(),
                startedAt,
                INITIAL_AGGREGATE_VERSION);
        return new TrackingStart(
                record, new TrackingStarted(trackingNumber.value(), bookingId, startedAt, record.aggregateVersion));
    }

    /** 保存されている状態から組み立てる（リポジトリが使う）。 */
    public static TrackingRecord reconstitute(
            TrackingNumber trackingNumber,
            UUID bookingId,
            CompanyId shipperCompanyId,
            CompanyId consigneeCompanyId,
            TrackedBookingStatus bookingStatus,
            Schedule schedule,
            TrackingStatus currentStatus,
            UtcInstant originalEta,
            UtcInstant latestEta,
            UtcInstant startedAt,
            long aggregateVersion) {
        return new TrackingRecord(
                trackingNumber,
                bookingId,
                shipperCompanyId,
                consigneeCompanyId,
                bookingStatus,
                schedule,
                currentStatus,
                originalEta,
                latestEta,
                startedAt,
                aggregateVersion);
    }

    public TrackingNumber trackingNumber() {
        return trackingNumber;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public CompanyId shipperCompanyId() {
        return shipperCompanyId;
    }

    public CompanyId consigneeCompanyId() {
        return consigneeCompanyId;
    }

    public TrackedBookingStatus bookingStatus() {
        return bookingStatus;
    }

    public Schedule schedule() {
        return schedule;
    }

    public TrackingStatus currentStatus() {
        return currentStatus;
    }

    /** 当初の到着予定（確定した経路版。T-INV-10）。 */
    public UtcInstant originalEta() {
        return originalEta;
    }

    /** 最新の到着見込み（T-INV-10）。 */
    public UtcInstant latestEta() {
        return latestEta;
    }

    /** 追跡を開始した時刻（追跡記録の作成時刻）。 */
    public UtcInstant startedAt() {
        return startedAt;
    }

    public long aggregateVersion() {
        return aggregateVersion;
    }
}
