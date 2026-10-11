package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * 回復コードのハッシュ。回復コードのハッシュを作り、照合するポート（SEC-09）。
 * 実装は `identity.infrastructure` の bcrypt で、ドメインは Spring Security の型を知らない（AT-01）。
 */
public interface RecoveryCodeHasher {

    /** 平文の回復コードのハッシュを作る。同じ平文でも塩で毎回違う値になってよい。 */
    String hash(String code);

    /** 平文の回復コードがハッシュに一致するか。 */
    boolean matches(String code, String hash);
}
