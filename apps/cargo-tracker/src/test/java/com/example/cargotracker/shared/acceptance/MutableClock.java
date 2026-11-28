package com.example.cargotracker.shared.acceptance;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 受入シナリオで現在時刻を固定するための Clock。
 */
public class MutableClock extends Clock {

    private Instant instant = Instant.EPOCH;

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("受入シナリオの時刻は UTC だけを扱う");
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
