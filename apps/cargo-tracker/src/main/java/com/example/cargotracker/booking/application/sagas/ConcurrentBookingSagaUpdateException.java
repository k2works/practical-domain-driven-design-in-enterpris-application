package com.example.cargotracker.booking.application.sagas;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;

/**
 * 予約サガを読んだ後に、ほかの更新が先に保存されていた（楽観ロックの競合。Bolt 25）。同時の DE-22 の負けた側で起き、例外のまま
 * トランザクションを戻して再配信に任せる（再配信では完了済みとして何もしない）。
 */
public class ConcurrentBookingSagaUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConcurrentBookingSagaUpdateException(BookingId bookingId, long expectedVersion) {
        super("予約 " + bookingId.value() + " の予約サガは版 " + expectedVersion + " の後に更新されています");
    }
}
