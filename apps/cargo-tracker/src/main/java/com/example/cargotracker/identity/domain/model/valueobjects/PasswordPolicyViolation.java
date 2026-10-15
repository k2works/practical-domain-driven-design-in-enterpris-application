package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * password の方針の違反。新しい password が password の方針（SEC-04・SEC-05）に合わない理由（Bolt 33a）。画面はこの種類から文言を選ぶ。
 */
@ValueObject
public enum PasswordPolicyViolation {
    /** 12 文字より短い（空を含む）。 */
    TOO_SHORT,
    /** 64 文字より長い。 */
    TOO_LONG,
    /** UTF-8 で 72 バイトを超える（bcrypt の上限。全角は 1 文字を 3 バイトで数える）。 */
    TOO_MANY_BYTES,
    /** よく使われる password の一覧に載っている。 */
    COMMON
}
