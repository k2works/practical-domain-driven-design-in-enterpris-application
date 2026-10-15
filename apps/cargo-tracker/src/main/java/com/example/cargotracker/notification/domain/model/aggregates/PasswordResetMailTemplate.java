package com.example.cargotracker.notification.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UtcInstant;

/**
 * password の再設定のメールの文面を組み立てるポート（UI 設計「password の再設定のメール」。Bolt 33）。実装は
 * {@code notification.infrastructure.mail} の Thymeleaf のテキストのテンプレート。本文に業務の情報を書かない（N-INV-01）。
 */
@FunctionalInterface
public interface PasswordResetMailTemplate {

    /**
     * 文面を組み立てる。
     *
     * @param to 宛先のメールアドレス
     * @param resetUrl 再設定のリンク
     * @param expiresAt リンクの期限
     * @param locale 文面の言語
     */
    OutgoingMail compose(String to, String resetUrl, UtcInstant expiresAt, String locale);
}
