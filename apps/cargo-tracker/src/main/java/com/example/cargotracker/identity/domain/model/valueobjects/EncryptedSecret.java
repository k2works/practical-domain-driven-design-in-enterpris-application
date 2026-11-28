package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 暗号化した秘密。TOTP の秘密を暗号化した文字列で、平文の秘密は表にも記録にも書かない（SEC-16、データモデル
 * `totp_credential.secret_encrypted`）。暗号化の方式と鍵は Bolt 31 で決め、この型は暗号文を受け渡すだけにする。
 * 文字列にしても暗号文を出さない（記録への漏れを防ぐ）。
 *
 * @param value 暗号文
 */
@ValueObject
public record EncryptedSecret(String value) {

    /** 長さの上限（データモデル VARCHAR(500)）。 */
    public static final int MAX_LENGTH = 500;

    public EncryptedSecret {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("暗号化した秘密がない");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("暗号化した秘密は " + MAX_LENGTH + " 文字まで");
        }
    }

    @Override
    public String toString() {
        return "EncryptedSecret[***]";
    }
}
