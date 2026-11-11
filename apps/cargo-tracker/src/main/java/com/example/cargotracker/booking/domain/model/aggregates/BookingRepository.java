package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.ProcessedCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 貨物予約のリポジトリ（送信ポート）。
 */
public interface BookingRepository {

    /**
     * 確定した貨物予約を予約版と処理済みコマンドとあわせて、同じトランザクションで追加する（B-INV-03。Bolt 24）。
     *
     * @param booking 確定した貨物予約
     * @param operator 操作した利用者 ID
     * @param processedCommand 確定のコマンドの記録
     * @throws DuplicateBookingException 同じ見積りの予約がすでにあるとき（B-INV-11）。トランザクションは続けて使える
     */
    void save(Booking booking, UUID operator, ProcessedCommand processedCommand);

    /** コマンド ID で確定結果を取得する（再送に最初の結果を返す。B-INV-03。Bolt 24）。 */
    Optional<ProcessedCommand> findProcessedCommand(CommandId commandId);

    /**
     * 業務番号と見積り番号で、同じ見積りの貨物予約の追跡番号を取得する（見積りの照会より前に確定済みを判定する。B-INV-11。Bolt 24）。
     */
    Optional<TrackingNumber> findTrackingNumber(String transportRequestNumber, int quotationNo);

    Optional<Booking> findByTrackingNumber(TrackingNumber trackingNumber);

    /**
     * 予約 ID の予約の行を押さえてから組み立てる（集荷・引渡しの通知が同じ予約に重なっても直列にする。Bolt 39g2）。呼び手のトランザクションの中でだけ
     * 呼べる。待ちの上限を超えたら技術の失敗として例外を上げる。
     *
     * @param id 予約 ID
     * @return 押さえた予約。行がなければ空
     */
    Optional<Booking> findByIdForUpdate(BookingId id);

    /**
     * 予約の状態と輸送段階を期待版（{@link Booking#aggregateVersion()}）で保存し、版を 1 進める（Bolt 39g2）。予約版は書かない（B-INV-08）。
     *
     * @param booking 状態と輸送段階を進めた予約
     * @param operator 操作者（予約の更新者に書く。集荷・引渡しを登録・採用した追跡管理者）
     * @throws org.springframework.dao.OptimisticLockingFailureException 期待版が保存されている版と違うとき（行を押さえた後には起きないはずの誤り）
     */
    void update(Booking booking, UUID operator);

    /**
     * 荷主企業の集合（営業担当者の担当企業）の予約の要約を、確定時刻（予約版 1 の commit 時刻）の新しい順（同じ時刻なら追跡番号の順）に
     * 上限まで返す（S-10。Bolt 25b。Bolt 38f2 で全件の口から置き換えた）。
     *
     * @param shipperCompanyIds 荷主企業 ID の集合（空なら空を返す。照会のサービスも先に空を守る）
     * @param limit 上限の件数
     * @return 予約の要約
     */
    List<BookingSummary> findRecentSummaries(Set<CompanyId> shipperCompanyIds, int limit);

    /**
     * 荷主企業の予約の要約を、確定時刻（予約版 1 の commit 時刻）の新しい順（同じ時刻なら追跡番号の順）に上限まで返す（C-06。他社の予約は
     * 返さない。BR-07。Bolt 27b）。
     *
     * @param shipperCompanyId 荷主企業 ID
     * @param limit 上限の件数
     * @return 予約の要約
     */
    List<BookingSummary> findRecentSummariesByShipper(CompanyId shipperCompanyId, int limit);

    /** 追跡番号がすでに使われているか（発行の前に確かめる）。 */
    boolean existsByTrackingNumber(TrackingNumber trackingNumber);
}
