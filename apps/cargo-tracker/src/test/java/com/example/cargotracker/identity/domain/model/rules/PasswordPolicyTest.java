package com.example.cargotracker.identity.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.valueobjects.PasswordPolicyViolation;
import org.junit.jupiter.api.Test;

/** password の方針（SEC-04・SEC-05。Bolt 33a）。 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void 長さは12文字から64文字まで() {
        assertThat(policy.violation("a".repeat(11), false)).contains(PasswordPolicyViolation.TOO_SHORT);
        assertThat(policy.violation("a".repeat(12), false)).isEmpty();
        assertThat(policy.violation("a".repeat(64), false)).isEmpty();
        assertThat(policy.violation("a".repeat(65), false)).contains(PasswordPolicyViolation.TOO_LONG);
    }

    @Test
    void 文字数はcodepointで数え空白と全角を許す() {
        // サロゲートペアの 12 文字は 24 の char だが 12 文字
        assertThat(policy.violation("😀".repeat(11), false)).contains(PasswordPolicyViolation.TOO_SHORT);
        assertThat(policy.violation("😀".repeat(12), false)).as("48 バイト").isEmpty();
        assertThat(policy.violation("correct horse battery", false)).isEmpty();
        assertThat(policy.violation("ぱすわーどはながいほうがよい", false))
                .as("全角 14 文字・42 バイト")
                .isEmpty();
    }

    @Test
    void UTF8で72バイトを超えると受け付けない() {
        // 全角 24 文字は 72 バイト、25 文字は 75 バイト
        assertThat(policy.violation("あ".repeat(24), false)).isEmpty();
        assertThat(policy.violation("あ".repeat(25), false)).contains(PasswordPolicyViolation.TOO_MANY_BYTES);
        assertThat(policy.violation("a".repeat(49) + "あ".repeat(8), false))
                .as("57 文字・73 バイト")
                .contains(PasswordPolicyViolation.TOO_MANY_BYTES);
        assertThat(policy.violation("a".repeat(48) + "あ".repeat(8), false))
                .as("56 文字・72 バイト")
                .isEmpty();
    }

    @Test
    void よく使われるpasswordは受け付けない() {
        assertThat(policy.violation("aaaaaaaaaaaa", true)).contains(PasswordPolicyViolation.COMMON);
    }

    @Test
    void 長さの誤りはよく使われるかより先に示す() {
        assertThat(policy.violation("short", true)).contains(PasswordPolicyViolation.TOO_SHORT);
    }
}
