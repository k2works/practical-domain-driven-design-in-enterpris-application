package com.example.cargotracker.shared.domain;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.time.Instant;
import java.util.Objects;

/**
 * UTC 時点。業務上の時刻は UTC の時点として保持する（BR-10）。
 *
 * @param instant 時点
 */
@ValueObject
public record UtcInstant(Instant instant) {

    public UtcInstant {
        Objects.requireNonNull(instant, "instant");
    }
}
