package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import java.util.Optional;
import java.util.UUID;

/**
 * 貨物予約のリポジトリ（送信ポート）。
 */
public interface BookingRepository {

    /**
     * 確定した貨物予約を予約版とあわせて追加する。
     *
     * @param booking 確定した貨物予約
     * @param operator 操作した利用者 ID
     * @throws DuplicateBookingException 同じ見積りの予約がすでにある、または追跡番号が重なったとき（B-INV-11）
     */
    void save(Booking booking, UUID operator);

    Optional<Booking> findByTrackingNumber(TrackingNumber trackingNumber);

    /** 追跡番号がすでに使われているか（発行の前に確かめる）。 */
    boolean existsByTrackingNumber(TrackingNumber trackingNumber);

    /** 見積りの貨物予約がすでにあるか（S-09 を開いたときの確定済みの判定。B-INV-11。Bolt 23b）。 */
    boolean existsByQuotationId(UUID quotationId);
}
