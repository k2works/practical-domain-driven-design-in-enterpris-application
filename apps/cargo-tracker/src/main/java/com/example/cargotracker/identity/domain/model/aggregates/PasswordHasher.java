package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * password のハッシュ。新しい password の保存の形（DelegatingPasswordEncoder の形式。SEC-07）を作るポート（Bolt 33a）。実装は
 * {@code identity.infrastructure.crypto} の bcrypt で、ドメインは Spring Security の型を知らない（外部の技術のポートはドメインに置く。ADR-017）。
 */
@FunctionalInterface
public interface PasswordHasher {

    /** 平文の password の保存の形（{@code {bcrypt}...}）。72 バイト以下の password だけを渡す（{@code PasswordPolicy}）。 */
    String hash(String password);
}
