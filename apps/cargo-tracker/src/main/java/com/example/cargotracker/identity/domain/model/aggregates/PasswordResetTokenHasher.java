package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * 再設定のトークンのハッシュ。リンクに入れる値の SHA-256 のハッシュ（16 進 64 文字）を作るポート（SEC-08。Bolt 33）。
 * 乱数の値は推測できないので塩を使わない（ハッシュで探せる）。実装は {@code identity.infrastructure.crypto}。
 */
@FunctionalInterface
public interface PasswordResetTokenHasher {

    /** 値の SHA-256 のハッシュ（16 進、小文字）。 */
    String hash(String token);
}
