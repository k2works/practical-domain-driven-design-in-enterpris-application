package com.example.cargotracker.tracking.acceptance;

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

    /** PostgreSQL の照会と同じく、追跡の開始時刻の新しい順（同じ時刻なら追跡番号の順）に上限まで返す（Bolt 26）。 */
    @Override
    public synchronized List<TrackingRecordSummary> findRecentSummaries(int limit) {
        return byBookingId.values().stream()
                .sorted(Comparator.comparing(
                                (TrackingRecord saved) -> saved.startedAt().instant())
                        .reversed()
                        .thenComparing(saved -> saved.trackingNumber().value()))
                .limit(limit)
                .map(saved -> new TrackingRecordSummary(
                        saved.trackingNumber(), saved.currentStatus(), saved.originalEta(), saved.startedAt()))
                .toList();
    }

    public synchronized List<TrackingRecord> all() {
        return List.copyOf(byBookingId.values());
    }

    public synchronized void clear() {
        byBookingId.clear();
    }
}
