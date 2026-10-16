package com.example.cargotracker.identity.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** session の方針の期限の判定（Bolt 33e）。ちょうどの時刻は期限切れ。 */
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
}
