package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * 使えない password のハッシュの発行。システム管理者が登録した利用者の、本人が初回の password を設定するまでの password のハッシュを作る
 * ポート（Bolt 38b2。SEC-07）。平文は誰にも渡さずに捨てるので、どの password とも一致しない。照合の時間は普通の利用者と同じ（壊れた
 * ハッシュのようにすぐ返らず、未設定かが応答の時間から分からない）。実装は {@code identity.infrastructure.crypto}（外部の技術のポートは
 * ドメインに置く。ADR-017）。
 */
@FunctionalInterface
public interface UnusablePasswordHashIssuer {

    /** 使えない password のハッシュ（{@code {bcrypt}...}）。 */
    String issue();
}
