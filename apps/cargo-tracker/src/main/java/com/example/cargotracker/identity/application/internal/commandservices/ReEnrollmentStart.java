package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;

/** 認証アプリの再登録を始めた結果（Bolt 33b、US-18 AC9）。 */
public sealed interface ReEnrollmentStart {

    /**
     * 始めた。新しい秘密の暗号文（画面は session に置く）と、認証アプリに示す値を返す。
     *
     * @param secret 新しい秘密の暗号文
     * @param provisioning QR コードとキー
     */
    record Started(EncryptedSecret secret, TotpProvisioning provisioning) implements ReEnrollmentStart {}

    /** 再登録できない（資格がない・利用停止・企業の無効。どれかは示さない）。 */
    record Unavailable() implements ReEnrollmentStart {}
}
