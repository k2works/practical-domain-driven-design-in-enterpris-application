package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 追跡記録のリポジトリ（送信ポート）。
 */
public interface TrackingRecordRepository {

    /**
     * 追跡を開始した追跡記録を予定区間とあわせて追加する。同じ予約の追跡記録がすでにあれば、予約 ID の一意制約の違反で失敗する
     * （T-INV-11。同時の配信の負けた側は、例外のままトランザクションを戻し、再配信に任せる）。
     */
    void save(TrackingRecord trackingRecord);

    /**
     * 追跡記録を更新する。まだ保存していない主要実績を追加し、現在状態・根拠の実績番号を書き直して版を 1 増やす（Bolt 26b）。
     * 読み込んだ後に別の更新が先に保存されていた（版の不一致）か、同じ出典の実績が同時に登録されていた（一意制約の違反）なら
     * {@link ConcurrentTrackingRecordUpdateException}。
     */
    void update(TrackingRecord trackingRecord);

    /** 予約 ID で追跡記録を取得する（DE-07 の再配信の冪等。T-INV-11）。 */
    Optional<TrackingRecord> findByBookingId(UUID bookingId);

    /** 追跡番号で追跡記録を予定区間とあわせて取得する（S-12。Bolt 26）。 */
    Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber);

    /**
     * 荷主企業の追跡記録を追跡番号で取得する（C-10。荷主企業で絞り、他社の追跡記録は存在しないのと同じく空。BR-07。Bolt 27）。荷主の
     * 照会はこちらだけを使い、荷主企業で絞らない {@link #findByTrackingNumber(TrackingNumber)} は使わない。
     */
    Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber, CompanyId shipperCompanyId);

    /**
     * 荷主企業の追跡記録の要約を、追跡の開始時刻の新しい順（同じ時刻なら追跡番号の順）に上限まで取得する（C-10。BR-07。Bolt 27）。
     *
     * @param limit 上限の件数（1 以上）
     */
    List<TrackingRecordSummary> findRecentSummariesByShipper(CompanyId shipperCompanyId, int limit);

    /**
     * 追跡記録の要約を、追跡の開始時刻の新しい順（同じ時刻なら追跡番号の順）に上限まで取得する（S-11。集約を組み立てない。Bolt 26）。
     *
     * @param limit 上限の件数（1 以上）
     */
    List<TrackingRecordSummary> findRecentSummaries(int limit);
}
