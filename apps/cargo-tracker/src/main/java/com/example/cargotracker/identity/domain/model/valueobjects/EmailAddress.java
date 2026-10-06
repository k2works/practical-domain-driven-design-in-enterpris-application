package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Locale;

/**
 * メールアドレス。利用者のログインの識別子。前後の空白を除き小文字にして比べる（データモデル `app_user.email`）。
 *
 * @param value 正規化したメールアドレス
 */
@ValueObject
public record EmailAddress(String value) {

    /** 長さの上限（RFC 5321 の経路を含む上限。データモデル VARCHAR(320)）。 */
    public static final int MAX_LENGTH = 320;

    public EmailAddress {
        if (value == null || !value.equals(normalize(value))) {
            throw new IllegalArgumentException("メールアドレスは正規化して渡す");
        }
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            throw new IllegalArgumentException("メールアドレスにはアットマークの前後が要る");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("メールアドレスは " + MAX_LENGTH + " 文字まで");
        }
    }

    /** 入力から作る。前後の空白を除き小文字にする。 */
    public static EmailAddress of(String input) {
        if (input == null) {
            throw new IllegalArgumentException("メールアドレスがない");
        }
        return new EmailAddress(normalize(input));
    }

    private static String normalize(String input) {
        return input.strip().toLowerCase(Locale.ROOT);
    }
}
