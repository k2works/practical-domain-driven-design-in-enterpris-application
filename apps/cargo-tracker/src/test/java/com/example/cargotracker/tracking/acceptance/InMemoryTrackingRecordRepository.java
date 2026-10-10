package com.example.cargotracker.tracking.acceptance;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentTrackingRecordUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * メモリ上の追跡記録のリポジトリ（業務ルール層の受入シナリオと単体テスト。Bolt 25）。追跡記録は不変なので保存したインスタンスを返す（T-61）。
 * 予約 ID の一意（T-INV-11）と追跡番号の一意は、PostgreSQL の一意制約と同じく保存のときに守る。
 */
public class InMemoryTrackingRecordRepository implements TrackingRecordRepository {

    private final Map<UUID, TrackingRecord> byBookingId = new LinkedHashMap<>();

    @Override
    public synchronized void save(TrackingRecord trackingRecord) {
        boolean sameTrackingNumber = byBookingId.values().stream()
                .anyMatch(saved -> saved.trackingNumber().equals(trackingRecord.trackingNumber()));
        if (byBookingId.containsKey(trackingRecord.bookingId()) || sameTrackingNumber) {
            throw new IllegalStateException("予約ごとに追跡記録は 1 件: " + trackingRecord.bookingId());
        }
        byBookingId.put(trackingRecord.bookingId(), trackingRecord);
    }

    /**
     * PostgreSQL と同じく、保存されている版が読み込んだときの版と同じときだけ置き換え、版を 1 増やす（Bolt 26b）。追跡記録は不変なので、
     * 版を増やした追跡記録を組み立て直して置く（写しは要らない）。
     */
    @Override
    public synchronized void update(TrackingRecord trackingRecord) {
        TrackingRecord saved = byBookingId.get(trackingRecord.bookingId());
        if (saved == null || saved.aggregateVersion() != trackingRecord.aggregateVersion()) {
            throw new ConcurrentTrackingRecordUpdateException(
                    trackingRecord.trackingNumber(), trackingRecord.aggregateVersion());
        }
        byBookingId.put(
                trackingRecord.bookingId(),
                TrackingRecord.reconstitute(
                        trackingRecord.trackingNumber(),
                        trackingRecord.bookingId(),
                        trackingRecord.shipperCompanyId(),
                        trackingRecord.consigneeCompanyId(),
                        trackingRecord.bookingStatus(),
                        trackingRecord.schedule(),
                        trackingRecord.currentStatus(),
                        trackingRecord.statusBasisMilestoneNo(),
                        trackingRecord.milestones(),
                        trackingRecord.originalEta(),
                        trackingRecord.latestEta(),
                        trackingRecord.startedAt(),
                        trackingRecord.aggregateVersion() + 1));
    }

    @Override
    public synchronized Optional<TrackingRecord> findByBookingId(UUID bookingId) {
        return Optional.ofNullable(byBookingId.get(bookingId));
    }

    @Override
    public synchronized Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber) {
        return byBookingId.values().stream()
                .filter(saved -> saved.trackingNumber().equals(trackingNumber))
                .findFirst();
    }

    /** PostgreSQL の照会と同じく、荷主企業で絞る（他社は空。Bolt 27）。 */
    @Override
    public synchronized Optional<TrackingRecord> findByTrackingNumber(
            TrackingNumber trackingNumber, CompanyId shipperCompanyId) {
        return findByTrackingNumber(trackingNumber)
                .filter(saved -> saved.shipperCompanyId().equals(shipperCompanyId));
    }

    /** PostgreSQL の照会と同じく、荷主企業で絞って追跡の開始時刻の新しい順に上限まで返す（Bolt 27）。 */
    @Override
    public synchronized List<TrackingRecordSummary> findRecentSummariesByShipper(
            CompanyId shipperCompanyId, int limit) {
        return summaries(
                byBookingId.values().stream()
                        .filter(saved -> saved.shipperCompanyId().equals(shipperCompanyId)),
                limit);
    }

    /** PostgreSQL の照会と同じく、追跡の開始時刻の新しい順（同じ時刻なら追跡番号の順）に上限まで返す（Bolt 26）。 */
    @Override
    public synchronized List<TrackingRecordSummary> findRecentSummaries(int limit) {
        return summaries(byBookingId.values().stream(), limit);
    }

    private static List<TrackingRecordSummary> summaries(Stream<TrackingRecord> records, int limit) {
        return records.sorted(Comparator.comparing(
                                (TrackingRecord saved) -> saved.startedAt().instant())
                        .reversed()
                        .thenComparing(saved -> saved.trackingNumber().value()))
                .limit(limit)
                .map(saved -> new TrackingRecordSummary(
                        saved.trackingNumber(),
                        saved.currentStatus(),
                        saved.originalEta(),
                        saved.latestEta(),
                        saved.startedAt()))
                .toList();
    }

    public synchronized List<TrackingRecord> all() {
        return List.copyOf(byBookingId.values());
    }

    public synchronized void clear() {
        byBookingId.clear();
    }
}
