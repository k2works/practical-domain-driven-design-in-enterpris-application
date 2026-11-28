package com.example.cargotracker.identity.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.rules.IdentityVerificationPolicy.Violation;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 本人確認の日時の範囲（Bolt 38c。行 7。今より後と、今から 7 日より前を拒む）。 */
class IdentityVerificationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:30:00Z");

    private static UtcInstant at(Duration offset) {
        return new UtcInstant(NOW.plus(offset));
    }

    @Test
    void 今と7日以内の日時は受け付ける() {
        UtcInstant now = at(Duration.ZERO);

        assertThat(IdentityVerificationPolicy.violationOf(now, now)).isEmpty();
        assertThat(IdentityVerificationPolicy.violationOf(at(Duration.ofDays(-3)), now))
                .isEmpty();
        assertThat(IdentityVerificationPolicy.violationOf(at(Duration.ofDays(-7)), now))
                .as("ちょうど 7 日前")
                .isEmpty();
    }

    @Test
    void 今より後の日時は未来として拒む() {
        assertThat(IdentityVerificationPolicy.violationOf(at(Duration.ofMinutes(1)), at(Duration.ZERO)))
                .contains(Violation.FUTURE);
    }

    @Test
    void 七日より前の日時は古すぎるとして拒む() {
        assertThat(IdentityVerificationPolicy.violationOf(at(Duration.ofDays(-7).minusMinutes(1)), at(Duration.ZERO)))
                .contains(Violation.TOO_OLD);
    }
}
