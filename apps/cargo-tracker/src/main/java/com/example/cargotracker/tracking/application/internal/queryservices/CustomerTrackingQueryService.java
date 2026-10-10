package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerTrackingView;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷主の追跡の照会（C-10。US-09 AC1。Bolt 27）。社内の照会（{@link TrackingRecordQueryService}）と分け、荷主の画面からはこちらだけを使う
 * （見積りの荷主と社内の照会のサービスの分け方と同じ）。荷主企業での絞り込みはリポジトリの照会で行い、他社の追跡記録は存在しないのと
 * 同じく扱う（BR-07）。荷主に見せる項目は集約の {@link TrackingRecord#customerView()} が決める（T-INV-09）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class CustomerTrackingQueryService {

    /** C-10 の一覧の上限の件数（S-11 と同じ。絞り込みとページ送りは後の Bolt）。 */
    private static final int RECENT_LIMIT = 50;

    private final TrackingRecordRepository repository;

    public CustomerTrackingQueryService(TrackingRecordRepository repository) {
        this.repository = repository;
    }

    /** 自社の追跡記録の一覧。追跡の開始時刻の新しい順に上限まで返し、上限を超えたかを示す（上限より 1 件多く引いて判定する）。 */
    @Transactional(readOnly = true)
    public RecentTrackingRecords recent(CompanyId shipperCompanyId) {
        List<TrackingRecordSummary> found = repository.findRecentSummariesByShipper(shipperCompanyId, RECENT_LIMIT + 1);
        return RecentTrackingRecords.of(found, RECENT_LIMIT);
    }

    /** 追跡番号で照会する。自社の追跡記録でなければ、存在しない追跡番号と同じく空を返す。 */
    @Transactional(readOnly = true)
    public Optional<CustomerTrackingView> find(TrackingNumber trackingNumber, CompanyId shipperCompanyId) {
        return repository.findByTrackingNumber(trackingNumber, shipperCompanyId).map(TrackingRecord::customerView);
    }
}
