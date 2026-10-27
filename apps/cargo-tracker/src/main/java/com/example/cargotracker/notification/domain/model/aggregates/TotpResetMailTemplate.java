package com.example.cargotracker.notification.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UtcInstant;

/**
 * 管理者による TOTP の登録の解除のお知らせの文面を組み立てるポート（UI 設計「認証アプリの登録の解除のご案内」。Bolt 38d）。実装は
 * {@code notification.infrastructure.mail} の Thymeleaf のテキストのテンプレート。本文に業務の情報（企業名・操作した人の名前・本人確認の
 * 手段・回復コードの値）を書かない（N-INV-01）。役割（システム管理者）だけを書く。
 */
@FunctionalInterface
public interface TotpResetMailTemplate {

    /**
     * 文面を組み立てる。
     *
     * @param to 宛先のメールアドレス
     * @param resetAt 解除の時刻
     * @param locale 文面の言語
     */
    OutgoingMail compose(String to, UtcInstant resetAt, String locale);
}
