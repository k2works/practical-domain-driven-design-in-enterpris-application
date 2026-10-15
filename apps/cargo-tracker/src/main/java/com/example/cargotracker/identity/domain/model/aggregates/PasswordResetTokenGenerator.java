package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * 再設定のトークンの生成器。リンクに入れる値を乱数で作るポート（SEC-08。Bolt 33）。実装は {@code identity.infrastructure.crypto}。
 */
@FunctionalInterface
public interface PasswordResetTokenGenerator {

    /** 32 バイトの乱数を URL 安全な Base64（パディングなし）にした値。 */
    String generate();
}
