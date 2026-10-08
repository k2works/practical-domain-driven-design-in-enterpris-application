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
    /** 荷主の承認済みでない。 */
    NOT_APPROVED
}
