package com.example.cargotracker.notification.domain.model.aggregates;

import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;

/**
 * 荷主への業務の通知の文面を組み立てるポート（US-22。Bolt 43）。実装は {@code notification.infrastructure.mail} の Thymeleaf のテキストの
 * テンプレート。本文は件名と画面への導線だけで、料金・契約条件・書類・他の予約の情報を書かない（AC3、N-INV-01）。
 */
@FunctionalInterface
public interface ShipperNotificationMailTemplate {

    /**
     * 文面を組み立てる。
     *
     * @param to 宛先のメールアドレス
     * @param kind 通知の種類（荷主への業務の通知の値）
     * @param targetRef 対象の参照（業務番号など）
     * @param link 画面への導線の URL
     * @param locale 文面の言語
     */
    OutgoingMail compose(String to, NotificationKind kind, String targetRef, String link, String locale);
}
