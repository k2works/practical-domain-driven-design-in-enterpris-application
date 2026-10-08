package com.example.cargotracker.booking.domain.model.aggregates;

/**
 * 同じ見積りの貨物予約がすでにある（B-INV-11、予約版の見積り ID の一意制約）か、追跡番号が重なった。確定の失敗として扱う。
 * 同じ見積りの重複の確定に既存の追跡番号を返す振る舞い（B-INV-03、AC4）は Bolt 24。
 */
public final class DuplicateBookingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateBookingException(String message, Throwable cause) {
        super(message, cause);
    }
}
