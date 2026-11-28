package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.UserId;

/**
 * TOTP の秘密の発行のポート（Bolt 31b、ADR-011）。秘密を乱数で作って持ち主に結び付けて暗号化し、登録の表示の値を暗号文から作る。
 * 実装は {@code identity.infrastructure} にあり、平文の秘密はポートの外に出さない。
 */
public interface TotpSecretIssuer {

    /** 新しい秘密を発行し、持ち主に結び付けた暗号文で返す。 */
    EncryptedSecret issue(UserId owner);

    /**
     * 暗号文から登録の表示の値を作る。
     *
     * @throws UnreadableCredentialException 別の利用者の暗号文、壊れた暗号文、知らない鍵の版（失敗へ倒す。T-42。Bolt 31d）
     */
    TotpProvisioning provisioning(UserId owner, EncryptedSecret secret, String accountName);
}
