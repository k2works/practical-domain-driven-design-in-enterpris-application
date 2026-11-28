package com.example.cargotracker.identity.domain.model.rules;

import com.example.cargotracker.identity.domain.model.valueobjects.PasswordPolicyViolation;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * password の方針。新しい password を受け付けるかを決める（SEC-04・SEC-05。Bolt 33a）。12 文字以上 64 文字以下（code point で数える。
 * 空白と全角を許す）、UTF-8 で 72 バイト以下（bcrypt の上限。SEC-07）、よく使われる password の一覧に載っていないこと。一覧の照合は呼ぶ側
 * （アプリケーションのサービスが一覧のポートで引く）が行い、結果だけを受ける（この規則はポートに依存しない）。正規化はしない（ログインの照合と
 * 合わせて扱う）。画面の文言の数値はこの定数から描く。
 */
@DomainRule
public class PasswordPolicy {

    /** 最小の文字数（SEC-04）。 */
    public static final int MIN_LENGTH = 12;

    /** 最大の文字数（SEC-04）。 */
    public static final int MAX_LENGTH = 64;

    /** UTF-8 での最大のバイト数（bcrypt の上限。SEC-04・SEC-07）。 */
    public static final int MAX_BYTES = 72;

    /**
     * 方針に合わない理由。合えば空。長さ・バイトの誤りを、よく使われるかより先に示す。
     *
     * @param password 新しい password
     * @param common よく使われる password の一覧に載っているか
     */
    public Optional<PasswordPolicyViolation> violation(String password, boolean common) {
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            return Optional.of(PasswordPolicyViolation.TOO_SHORT);
        }
        if (length > MAX_LENGTH) {
            return Optional.of(PasswordPolicyViolation.TOO_LONG);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of(PasswordPolicyViolation.TOO_MANY_BYTES);
        }
        if (common) {
            return Optional.of(PasswordPolicyViolation.COMMON);
        }
        return Optional.empty();
    }
}
