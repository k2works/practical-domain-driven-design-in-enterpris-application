package com.example.cargotracker.shared.domain;

/**
 * 荷主への業務の通知の種類（共有カーネル。通知の公開 API の要求が使う。ADR-017。US-22。Bolt 43）。通知の {@code kind}（データモデル）と同じ名前で、公開 API の外へ通知の型を出さないための
 * 写し。
 */
public enum ShipperNotificationKind {
    /** 見積りが提示された。 */
    QUOTATION_PRESENTED,
    /** 経路が承認され、荷主の承認が必要になった。 */
    ROUTE_APPROVED,
    /** 承認待ちの見積りの有効期限が近い（24 時間前）。 */
    QUOTATION_EXPIRING,
    /** 本予約が確定した。 */
    BOOKING_CONFIRMED,
    /** 追跡が確認中になった。 */
    TRACKING_UNDER_REVIEW
}
