package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;

/**
 * TOTP の生成器。暗号化した秘密と時間の刻みから TOTP のコードを作るポート（ADR-011）。平文の秘密はドメインとアプリケーションの層に
 * 出さない（Bolt 31、SEC-16）。実装は `identity.infrastructure` の
 * java-otp で、ドメインはライブラリを知らない（ArchUnit）。時間の窓の照合と再利用の拒否は {@link TotpCredential} が持つ。
 */
@FunctionalInterface
public interface TotpCodeGenerator {

    /**
     * その刻みのコードを作る。
     *
     * @param owner 秘密の持ち主（暗号文は持ち主に結び付く。AAD。Bolt 31b）
     * @param secret 暗号化した秘密。復号と平文の消去は実装の中で済ませる
     * @param step 時間の刻みの番号（epoch 秒 / 30）。0 以上
     * @return 先頭の 0 を保った固定の桁数のコード
     */
    /**
     * その刻みのコードを作る。
     *
     * @throws UnreadableCredentialException 暗号文を読めない（知らない鍵の版・壊れた暗号文・別の持ち主の暗号文。Bolt 31d）
     */
    String code(UserId owner, EncryptedSecret secret, long step);
}
