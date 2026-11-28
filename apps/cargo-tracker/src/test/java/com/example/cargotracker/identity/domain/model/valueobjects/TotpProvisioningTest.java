package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 登録の表示の値（Bolt 31b）。URI と手入力のキーは秘密と同じ重さなので、文字列にしても値を出さない（記録への漏れを防ぐ）。
 */
class TotpProvisioningTest {

    @Test
    void 文字列にしても秘密と同じ重さの値を出さない() {
        TotpProvisioning provisioning =
                new TotpProvisioning("otpauth://totp/x?secret=ABCDEFGH", "ABCD EFGH", "<svg>secret</svg>");

        assertThat(provisioning.toString())
                .doesNotContain("ABCDEFGH")
                .doesNotContain("ABCD EFGH")
                .doesNotContain("<svg>");
    }

    @Test
    void 値がないものは作らない() {
        assertThatThrownBy(() -> new TotpProvisioning(null, "ABCD", "<svg/>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TotpProvisioning("otpauth://x", " ", "<svg/>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TotpProvisioning("otpauth://x", "ABCD", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
