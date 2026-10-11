package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * TOTP の生成器。秘密と時間の刻みから TOTP のコードを作るポート（ADR-011）。実装は `identity.infrastructure` の
 * java-otp で、ドメインはライブラリを知らない（ArchUnit）。時間の窓の照合と再利用の拒否は {@link TotpCredential} が持つ。
 */
@FunctionalInterface
public interface TotpCodeGenerator {

    /**
     * その刻みのコードを作る。
     *
     * @param secret 復号した秘密（平文。呼び出し側が使い終わったら消す）
     * @param step 時間の刻みの番号（epoch 秒 / 30）。0 以上
     * @return 先頭の 0 を保った固定の桁数のコード
     */
    String code(byte[] secret, long step);
}
