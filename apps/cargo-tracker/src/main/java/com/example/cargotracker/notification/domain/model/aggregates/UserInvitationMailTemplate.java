package com.example.cargotracker.notification.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UtcInstant;

/**
 * 利用者の招待のメールの文面を組み立てるポート（UI 設計「利用者の招待のメール」。Bolt 38b2b）。実装は
 * {@code notification.infrastructure.mail} の Thymeleaf のテキストのテンプレート。本文に業務の情報（企業名・役割・登録した人）を書かない
 * （N-INV-01）。
 */
@FunctionalInterface
public interface UserInvitationMailTemplate {

    /**
     * 文面を組み立てる。
     *
     * @param to 宛先のメールアドレス
     * @param invitationUrl 初回の設定のリンク
     * @param expiresAt リンクの期限
     * @param locale 文面の言語
     */
    OutgoingMail compose(String to, String invitationUrl, UtcInstant expiresAt, String locale);
}
