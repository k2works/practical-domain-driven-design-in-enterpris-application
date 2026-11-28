package com.example.cargotracker.shared.acceptance;

import io.cucumber.java.ja.前提;
import java.time.Instant;

/**
 * 共通ステップ: 時刻の固定。
 */
public class ClockSteps {

    private final MutableClock clock;

    public ClockSteps(MutableClock clock) {
        this.clock = clock;
    }

    @前提("現在時刻が {string} である")
    public void 現在時刻が_である(String instant) {
        clock.setInstant(Instant.parse(instant));
    }
}
