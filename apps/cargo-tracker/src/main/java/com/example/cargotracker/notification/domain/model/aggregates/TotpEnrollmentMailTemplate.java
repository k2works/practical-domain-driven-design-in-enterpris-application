package com.example.cargotracker.notification.domain.model.aggregates;

import com.example.cargotracker.shared.domain.UtcInstant;

/**
 * 認証アプリの登録と再登録のお知らせの文面を組み立てるポート（UI 設計「認証アプリの登録のご案内」「認証アプリの再登録のご案内」。Bolt 38d）。
 * 実装は {@code notification.infrastructure.mail} の Thymeleaf のテキストのテンプレート。本文に業務の情報（企業名・操作した人・回復コードの
 * 値）を書かない（N-INV-01）。
 */
@FunctionalInterface
public interface TotpEnrollmentMailTemplate {

    /**
     * 文面を組み立てる。
     *
     * @param to 宛先のメールアドレス
     * @param enrolledAt 登録か再登録の時刻
     * @param reEnrolled 回復コードの経路の再登録なら true（再登録の文面）
     * @param locale 文面の言語
     */
    OutgoingMail compose(String to, UtcInstant enrolledAt, boolean reEnrolled, String locale);
}
