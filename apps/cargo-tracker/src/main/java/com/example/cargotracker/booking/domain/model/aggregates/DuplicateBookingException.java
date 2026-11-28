package com.example.cargotracker.booking.domain.model.aggregates;

/**
 * 同時の確定がすでに決着している。同じ見積りの貨物予約がある（B-INV-11、見積り ID または業務番号と見積り番号の一意制約）か、同じ
 * コマンド ID の確定がすでに記録されている（B-INV-03、処理済みコマンドの主キー）。呼び出し側は同じトランザクションで読み直して、既存の
 * 追跡番号・最初の結果・衝突のどれかを返す（Bolt 24）。追跡番号の重なりはこの例外にせず、技術の失敗とする（Bolt 23 レビュー M-1）。
 */
public final class DuplicateBookingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateBookingException(String message, Throwable cause) {
        super(message, cause);
    }
}
