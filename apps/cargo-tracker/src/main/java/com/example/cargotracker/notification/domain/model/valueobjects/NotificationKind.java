package com.example.cargotracker.notification.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 通知の種類。通知の契機の区分（データモデル `notification.kind`）。Bolt 33 で password の再設定を、Bolt 38b2b で利用者の招待を作った。
 * 荷主への業務の通知の値（見積りの提示など）は W9 の US-22 で足す（表の CHECK 制約には先に置いてある）。
 */
@ValueObject
public enum NotificationKind {
    /** password の再設定のリンク（US-18 AC8、SEC-08）。対象はトークンの ID で、本文は再現しない。 */
    PASSWORD_RESET,
    /** 利用者の招待のリンク（US-16 AC1。Bolt 38b2b）。システム管理者が登録した利用者への初回の設定。対象はトークンの ID で、本文は再現しない。 */
    USER_INVITATION
}
