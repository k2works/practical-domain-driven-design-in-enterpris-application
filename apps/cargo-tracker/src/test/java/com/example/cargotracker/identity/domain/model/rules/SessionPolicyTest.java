package com.example.cargotracker.identity.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** session の方針の期限の判定（Bolt 33e）。ちょうどの時刻は期限切れ。 */
class SessionPolicyTest {

    private static final UtcInstant START = new UtcInstant(Instant.parse("2026-10-16T00:00:00Z"));
    private static final Duration LIFETIME = Duration.ofMinutes(15);

    @Test
    void 期間の1ナノ秒前は期限内でちょうどとその後は期限切れ() {
        Instant deadline = START.instant().plus(LIFETIME);

        assertThat(SessionPolicy.isExpired(START, at(deadline.minusNanos(1)), LIFETIME))
                .isFalse();
        assertThat(SessionPolicy.isExpired(START, at(deadline), LIFETIME)).isTrue();
        assertThat(SessionPolicy.isExpired(START, at(deadline.plusNanos(1)), LIFETIME))
                .isTrue();
    }

    @Test
    void 始めた時刻ちょうどは期限内() {
        assertThat(SessionPolicy.isExpired(START, START, LIFETIME)).isFalse();
    }

    private static UtcInstant at(Instant instant) {
        return new UtcInstant(instant);
    }
}
