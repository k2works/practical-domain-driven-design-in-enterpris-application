package com.example.cargotracker.booking.application.internal.outboundservices.acl;

/**
 * 見積りが本予約の確定に使えない理由（予約の言葉。見積りの公開 API の理由の名前を写す。ADR-016）。
 */
public enum QuotationUnavailability {
    /** 見積りが見つからない。 */
    NOT_FOUND,
    /** commit 時刻が有効期限と同時刻以後、または失効（BR-10。再見積りが必要）。 */
    EXPIRED,
    /** 置換済み。 */
    REPLACED,
    /** 荷主の承認済みでない（見積りの公開 API が予約の知らない理由を返したときもここに寄せる。Bolt 37e3）。 */
    NOT_APPROVED,
    /** 割り当てた経路版が再設計要（US-07 AC5、B-INV-05。Bolt 37e3）。 */
    ROUTE_REDESIGN_REQUIRED,
    /** 割り当てた経路の最初の区間が出発済み（D-88。US-07 AC5。Bolt 37e4）。 */
    ROUTE_ALREADY_DEPARTED
}
