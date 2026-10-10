package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡記録の照会（S-11 追跡一覧、S-12 追跡の詳細。Bolt 26）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class TrackingRecordQueryService {

    /** S-11 追跡一覧の上限の件数（Bolt 26。絞り込みとページ送りは W7 以後）。 */
    private static final int RECENT_LIMIT = 50;

    private final TrackingRecordRepository repository;

    public TrackingRecordQueryService(TrackingRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * 追跡一覧（S-11 の最小の表示）。追跡の開始時刻の新しい順に上限まで返し、上限を超えたかを示す。上限より 1 件多く引いて超えたかを
     * 判定し、要約は 1 回の照会で引く（行ごとに照会しない）。
     */
    @Transactional(readOnly = true)
    public RecentTrackingRecords recent() {
        List<TrackingRecordSummary> found = repository.findRecentSummaries(RECENT_LIMIT + 1);
        return RecentTrackingRecords.of(found, RECENT_LIMIT);
    }

    /** 追跡の詳細（S-12）。追跡記録を予定区間とあわせて返す。 */
    @Transactional(readOnly = true)
    public Optional<TrackingRecord> detail(TrackingNumber trackingNumber) {
        return repository.findByTrackingNumber(trackingNumber);
    }
}
