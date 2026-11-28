package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * 追跡記録を更新する。まだ保存していない主要実績を追加し、現在状態・根拠の実績番号・最新の到着見込み・最終取得時刻（Bolt 39g3。当初の到着予定は書かない）・次の予定の実績の予定時刻（Bolt 39g4）・航海の到着予定と順の鍵（Bolt 39g4b）を書き直して版を 1 増やす（Bolt 26b）。
     * 読み込んだ後に別の更新が先に保存されていた（版の不一致）か、同じ出典の実績が同時に登録されていた（一意制約の違反）なら
     * {@link ConcurrentTrackingRecordUpdateException}。表にある主要実績の中身が集約と違う、または表にあって集約にない実績があれば、版を通さずに書いた
     * 経路の誤りなので {@link IllegalStateException}（P-3。Bolt 39g2）。
     */
    void update(TrackingRecord trackingRecord);

    /** 予約 ID で追跡記録を取得する（DE-07 の再配信の冪等。T-INV-11）。 */
    Optional<TrackingRecord> findByBookingId(UUID bookingId);

    /** 追跡番号で追跡記録を予定区間とあわせて取得する（S-12。Bolt 26）。 */
    Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber);

    /**
     * DE-13 の受け取りのために、追跡番号の行を呼び手のトランザクションが終わるまで押さえてから、追跡記録を読み直す（Bolt 39g の決定 (4)）。同じ追跡番号の DE-13 を直列にする。
     * 追跡管理者の下書きの採用も、DE-13 の受け取りと直列にするためにこれで読み直す（Bolt 39gb の決定 (5a)）。DE-12 の受け取りも同じく押さえて読み直す
     * （Bolt 39g4b。3 つの目的に使うので {@code findByTrackingNumberForExternalMilestone} から名前を変えた）。
     * 待ちの上限は 10 秒で、超えると例外（発行記録を未完了に残す）。追跡記録がなければ押さえずに空を返す。呼び手のトランザクションの中で呼ぶ。
     */
    Optional<TrackingRecord> findByTrackingNumberForUpdate(TrackingNumber trackingNumber);

    /**
     * その航海が最後の区間（その追跡記録の最後の予定区間）の追跡番号を、追跡番号の順に取得する（DE-12 の受け取りの対象。Bolt 39g4b の決定 (6)）。並べるのは照会の
     * 責任で、呼び手はこの順に行を押さえる（デッドロックを避ける）。同じ追跡で最初と最後の区間に出る航海も 1 回だけ返す。完了後・取消済みは除かない（追跡記録が決める）。
     */
    List<TrackingNumber> findTrackingNumbersByFinalLegVoyage(String voyageNumber);

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
     * 荷主企業の集合の追跡記録の要約を、追跡の開始時刻の新しい順（同じ時刻なら追跡番号の順）に上限まで取得する（S-11。集約を組み立てない。
     * Bolt 26）。社内の画面は担当企業の集合で絞る（Bolt 38g。全件の口は置かない）。集合が空なら問い合わせずに空を返す。
     *
     * @param limit 上限の件数（1 以上）
     */
    List<TrackingRecordSummary> findRecentSummaries(Set<CompanyId> shipperCompanyIds, int limit);
}
