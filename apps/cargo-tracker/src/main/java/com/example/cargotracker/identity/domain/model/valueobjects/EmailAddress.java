package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

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
        // Red の骨組み: まだ検証も正規化もしない
    }

    /** 入力から作る。前後の空白を除き小文字にする。 */
    public static EmailAddress of(String input) {
        return new EmailAddress(input);
    }
}
