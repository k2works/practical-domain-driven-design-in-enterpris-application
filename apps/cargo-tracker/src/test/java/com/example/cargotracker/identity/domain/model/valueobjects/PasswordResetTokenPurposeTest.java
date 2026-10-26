package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 再設定のトークンの用途（Bolt 38b・38b2c。IA-INV-12）。 */
class PasswordResetTokenPurposeTest {

    @Test
    void 再設定は30分で繰り返しの依頼の抑えを数える() {
        // 行 8
        assertThat(PasswordResetTokenPurpose.RESET.validity()).isEqualTo(Duration.ofMinutes(30));
        assertThat(PasswordResetTokenPurpose.RESET.throttlesRepeatedIssue()).isTrue();
    }

    @Test
    void 利用者の招待は24時間で繰り返しの依頼の抑えを数えない() {
        // 行 8（招待の直後の本人の再設定を止めない）
        assertThat(PasswordResetTokenPurpose.USER_INVITATION.validity()).isEqualTo(Duration.ofHours(24));
        assertThat(PasswordResetTokenPurpose.USER_INVITATION.throttlesRepeatedIssue())
                .isFalse();
    }
}
