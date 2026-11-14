package com.example.cargotracker.notification.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 通知の種類。通知の契機の区分（データモデル `notification.kind`）。Bolt 33 で password の再設定を、Bolt 38b2b で利用者の招待を、
 * Bolt 38d で TOTP の解除・登録・再登録のお知らせを作った。荷主への業務の通知の値（見積りの提示など）は W9 の US-22 で足す（表の CHECK
 * 制約には先に置いてある）。
 */
@ValueObject
public enum NotificationKind {
    /** password の再設定のリンク（US-18 AC8、SEC-08）。対象はトークンの ID で、本文は再現しない。 */
    PASSWORD_RESET,
    /** 利用者の招待のリンク（US-16 AC1。Bolt 38b2b）。システム管理者が登録した利用者への初回の設定。対象はトークンの ID で、本文は再現しない。 */
    USER_INVITATION,
    /** システム管理者による TOTP の登録の解除のお知らせ（US-18 AC9、SEC-10。Bolt 38d）。対象は監査記録の ID で、本文は監査から再現できる。 */
    TOTP_RESET_BY_ADMIN,
    /** 認証アプリの登録（A-07）のお知らせ（SEC-10。Bolt 38d）。対象は監査記録の ID で、本文は監査から再現できる。 */
    TOTP_ENROLLED,
    /** 回復コードの経路での認証アプリの再登録（A-08）のお知らせ（SEC-10。Bolt 38d）。対象は監査記録の ID で、本文は監査から再現できる。 */
    TOTP_RE_ENROLLED,
    /** 見積りの提示（荷主への業務の通知。US-22 AC1。Bolt 43）。対象は業務番号で、本文は件名と画面への導線だけ（N-INV-01）。 */
    QUOTATION_PRESENTED,
    /** 経路の承認（荷主の承認が必要になった。US-22 AC1。Bolt 43）。 */
    ROUTE_APPROVED,
    /** 見積りの有効期限が近い（24 時間前。US-22 AC2。Bolt 43）。 */
    QUOTATION_EXPIRING,
    /** 本予約の確定（US-22 AC1。Bolt 43）。 */
    BOOKING_CONFIRMED,
    /** 追跡が確認中になった（US-22 AC1。DE-18。Bolt 43）。 */
    TRACKING_UNDER_REVIEW
}
