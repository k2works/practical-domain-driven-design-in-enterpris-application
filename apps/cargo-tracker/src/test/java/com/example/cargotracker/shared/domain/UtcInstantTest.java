package com.example.cargotracker.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UtcInstantTest {

    @Test
    void UTCの時点を保持する() {
        Instant instant = Instant.parse("2026-10-05T01:00:00Z");

        assertThat(new UtcInstant(instant).instant()).isEqualTo(instant);
    }

    @Test
    void 時点のないUTC時点は作れない() {
        assertThatThrownBy(() -> new UtcInstant(null)).isInstanceOf(NullPointerException.class);
    }
}
