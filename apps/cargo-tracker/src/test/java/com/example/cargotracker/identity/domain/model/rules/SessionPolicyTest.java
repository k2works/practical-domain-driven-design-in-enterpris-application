package com.example.cargotracker.identity.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** session の方針の期限の判定（Bolt 33e）と期限の時刻（Bolt 33f）。ちょうどの時刻は期限切れ。 */
class SessionPolicyTest {

    private static final UtcInstant START = new UtcInstant(Instant.parse("2026-10-16T00:00:00Z"));
    private static final Duration LIFETIME = Duration.ofMinutes(15);

    @ParameterizedTest(name = "期限から {0} ナノ秒: 期限切れは {1}")
    @CsvSource({"-900000000000, false", "-1, false", "0, true", "1, true"})
    void ちょうどの時刻からは期限切れ(long nanosFromDeadline, boolean expired) {
        Instant now = START.instant().plus(LIFETIME).plusNanos(nanosFromDeadline);

        assertThat(SessionPolicy.isExpired(START, new UtcInstant(now), LIFETIME))
                .isEqualTo(expired);
    }

    @Test
    void 期限は始めた時刻に期間を足した時刻() {
        assertThat(SessionPolicy.deadline(START, LIFETIME))
                .isEqualTo(new UtcInstant(Instant.parse("2026-10-16T00:15:00Z")));
    }

    @Test
    void 期限の時刻ちょうどは期限切れでその1ナノ秒前は期限内() {
        UtcInstant deadline = SessionPolicy.deadline(START, LIFETIME);

        assertThat(SessionPolicy.isExpired(START, deadline, LIFETIME)).isTrue();
        assertThat(SessionPolicy.isExpired(
                        START, new UtcInstant(deadline.instant().minusNanos(1)), LIFETIME))
                .isFalse();
    }

    @Test
    void 期間はどれも分で割り切れる() {
        // 画面に「15 分」「8 時間」と書くとき、秒を切り捨てて「0 分」などと出さない（Bolt 33f の開発レビュー）
        assertThat(java.util.List.of(
                        SessionPolicy.MAX_LIFETIME,
                        SessionPolicy.PASSWORD_STAGE_LIFETIME,
                        SessionPolicy.ENROLLMENT_LIFETIME,
                        SessionPolicy.RECOVERY_LOGIN_LIFETIME,
                        SessionPolicy.RE_ENROLLMENT_LIFETIME))
                .hasSize(5)
                .allMatch(
                        lifetime -> lifetime.toSecondsPart() == 0 && lifetime.toNanosPart() == 0 && !lifetime.isZero());
    }
}
