package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Clock はマイクロ秒に切り捨てる（ADR-007）。TIMESTAMP WITH TIME ZONE の精度に合わせ、保存の前後で時刻が変わらないようにする。
 */
class ClockConfigurationTest {

    @Test
    void ナノ秒を持つ時刻をマイクロ秒に切り捨てる() {
        Clock base = Clock.fixed(Instant.parse("2026-10-05T01:00:00.123456789Z"), ZoneOffset.UTC);

        assertThat(ClockConfiguration.microsecondClock(base).instant())
                .isEqualTo(Instant.parse("2026-10-05T01:00:00.123456Z"));
    }

    @Test
    void アプリケーションのClockはUTCで動く() {
        assertThat(new ClockConfiguration().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
