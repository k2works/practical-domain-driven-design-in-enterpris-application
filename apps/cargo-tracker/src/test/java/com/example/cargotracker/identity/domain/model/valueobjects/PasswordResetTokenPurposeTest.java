package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
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

    private static final Instant REGISTERED = Instant.parse("2026-10-26T05:00:00Z");

    private static UtcInstant at(Instant instant) {
        return new UtcInstant(instant);
    }

    @Test
    void 再設定は初回の設定でなく契機の時刻を問わず譲る相手がない() {
        // Bolt 38b4 の行 1
        assertThat(PasswordResetTokenPurpose.RESET.setsInitialPassword()).isFalse();
        assertThat(PasswordResetTokenPurpose.RESET.acceptsTriggerAt(
                        at(REGISTERED), at(REGISTERED.plus(Duration.ofDays(2)))))
                .isTrue();
        assertThat(PasswordResetTokenPurpose.RESET.yieldsTo()).isEmpty();
    }

    @Test
    void 利用者の招待は初回の設定で契機から24時間の前だけ発行してよく再設定に譲る() {
        // Bolt 38b4 の行 1（23 時間 59 分 59 秒は可、ちょうど 24 時間は不可）
        PasswordResetTokenPurpose invitation = PasswordResetTokenPurpose.USER_INVITATION;
        assertThat(invitation.setsInitialPassword()).isTrue();
        assertThat(invitation.acceptsTriggerAt(
                        at(REGISTERED), at(REGISTERED.plus(Duration.ofHours(24)).minusSeconds(1))))
                .isTrue();
        assertThat(invitation.acceptsTriggerAt(at(REGISTERED), at(REGISTERED.plus(Duration.ofHours(24)))))
                .isFalse();
        assertThat(invitation.yieldsTo()).contains(PasswordResetTokenPurpose.RESET);
    }
}
