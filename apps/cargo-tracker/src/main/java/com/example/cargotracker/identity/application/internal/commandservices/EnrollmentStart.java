package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import java.util.Objects;

/** TOTP の登録を始めた結果（Bolt 31b、US-18 AC7）。 */
public sealed interface EnrollmentStart {

    /**
     * 登録を始めた。暗号文は登録の途中の値として session に置き（文字列で）、表示の値は登録の画面に一度だけ渡す。
     *
     * @param secret 持ち主に結び付いた暗号文
     * @param provisioning 登録の表示の値（秘密と同じ重さ）
     */
    record Started(EncryptedSecret secret, TotpProvisioning provisioning) implements EnrollmentStart {

        public Started {
            Objects.requireNonNull(secret, "secret");
            Objects.requireNonNull(provisioning, "provisioning");
        }
    }

    /** 既に登録している（A-02 へ送る）。 */
    record AlreadyEnrolled() implements EnrollmentStart {}
}
