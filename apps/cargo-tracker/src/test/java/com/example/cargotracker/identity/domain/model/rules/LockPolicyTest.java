package com.example.cargotracker.identity.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LockPolicyTest {

    private final LockPolicy policy = new LockPolicy();

    @Test
    void 連続の失敗が5回に達したらロックする() {
        assertThat(policy.shouldLock(4)).isFalse();
        assertThat(policy.shouldLock(5)).isTrue();
        assertThat(policy.maxConsecutiveFailures()).isEqualTo(5);
    }

    @Test
    void ロックの期限は失敗の時刻から15分() {
        Instant failedAt = Instant.parse("2026-10-11T00:00:00Z");

        assertThat(policy.lockedUntil(new UtcInstant(failedAt)))
                .isEqualTo(new UtcInstant(failedAt.plus(Duration.ofMinutes(15))));
    }
}
