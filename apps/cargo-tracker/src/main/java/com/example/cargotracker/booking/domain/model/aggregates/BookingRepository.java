package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.ProcessedCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Optional;
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
     * 確定した予約の要約を、確定時刻（予約版 1 の commit 時刻）の新しい順（同じ時刻なら追跡番号の順）に上限まで返す（S-10。Bolt 25b）。
     *
     * @param limit 上限の件数
     * @return 予約の要約
     */
    List<BookingSummary> findRecentSummaries(int limit);

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
