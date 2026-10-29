package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡記録の照会（S-11 追跡一覧、S-12 追跡の詳細。Bolt 26）。操作者の担当企業で絞る（{@link TrackingRecordScope}。Bolt 38g。US-16 AC4）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class TrackingRecordQueryService {

    /** S-11 追跡一覧の上限の件数（Bolt 26。絞り込みとページ送りは W7 以後）。 */
    private static final int RECENT_LIMIT = 50;

    private final TrackingRecordRepository repository;
    private final TrackingRecordScope scope;

    public TrackingRecordQueryService(TrackingRecordRepository repository, TrackingRecordScope scope) {
        this.repository = repository;
        this.scope = scope;
    }

    /**
     * 追跡一覧（S-11 の最小の表示）。追跡の開始時刻の新しい順に上限まで返し、上限を超えたかを示す。上限より 1 件多く引いて超えたかを
     * 判定し、要約は 1 回の照会で引く（行ごとに照会しない）。
     */
    @Transactional(readOnly = true)
    public RecentTrackingRecords recent(AuthenticatedActor actor) {
        List<TrackingRecordSummary> found =
                scope.listWithin(actor, companies -> repository.findRecentSummaries(companies, RECENT_LIMIT + 1));
        return RecentTrackingRecords.of(found, RECENT_LIMIT);
    }

    /** 追跡の詳細（S-12・S-13）。担当範囲の中の追跡記録を予定区間とあわせて返す。ない・範囲の外は空（どちらも 404。記録しない）。 */
    @Transactional(readOnly = true)
    public Optional<TrackingRecord> detail(TrackingNumber trackingNumber, AuthenticatedActor actor) {
        return scope.find(trackingNumber, actor).inScope();
    }

    /**
     * 追跡記録が操作者の担当範囲の外か（マイルストーンの登録の POST の前の確かめ。ロックしない。Bolt 38g）。ない追跡記録は範囲の外では
     * ない（記録せず 404 はコマンドが返す）。
     */
    @Transactional(readOnly = true)
    public boolean outOfScope(TrackingNumber trackingNumber, AuthenticatedActor actor) {
        return scope.find(trackingNumber, actor) instanceof TrackingRecordLookup.OutOfScope;
    }
}
