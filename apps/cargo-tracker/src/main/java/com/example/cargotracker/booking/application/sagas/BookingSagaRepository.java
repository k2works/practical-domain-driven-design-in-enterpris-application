package com.example.cargotracker.booking.application.sagas;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 予約サガのリポジトリ（送信ポート）。
 */
public interface BookingSagaRepository {

    void save(BookingSaga saga);

    Optional<BookingSaga> findByBookingId(BookingId bookingId);

    /**
     * 予約サガの状態を期待版（読んだときの版）で更新し、版を 1 進める（ARCH-HO-01。Bolt 25）。
     *
     * @throws ConcurrentBookingSagaUpdateException 読んだ後に、ほかの更新が先に保存されていたとき（同時の DE-22 の負けた側。再配信に任せる）
     */
    void update(BookingSaga saga);

    /**
     * 予約 ID の集合の予約サガの状態をまとめて返す（S-10 予約一覧。行ごとに照会しない。Bolt 25b）。
     *
     * @param bookingIds 予約 ID の集合
     * @return 予約 ID ごとの予約サガの状態。予約サガのない予約 ID は含まない
     */
    Map<BookingId, BookingSagaStatus> findStatusesByBookingIds(Set<BookingId> bookingIds);
}
