package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 追跡番号（BR-07。Bolt 23）。CT と、紛らわしい文字（0・O・1・I・L）を除いた英大文字・数字 12 桁。 */
class TrackingNumberTest {

    @Test
    void 乱数から形式どおりの追跡番号を作る() {
        Random random = new Random(42);
        Set<String> issued = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            TrackingNumber number = TrackingNumber.generate(random);
            assertThat(number.value()).matches("CT[A-HJKMNP-Z2-9]{12}");
            issued.add(number.value());
        }
        assertThat(issued).hasSize(1000);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "CTABCDEFGH234",
                "CTABCDEFGH23456",
                "XXABCDEFGH2345",
                "CTABCDEFGH234O",
                "CTABCDEFGH2341",
                "CTabcdefgh2345",
                "CTABCDEFGH234L",
                "CTABCDEFGH234I",
                "CTABCDEFGH2340"
            })
    void 形式に合わない追跡番号は作れない(String value) {
        assertThatThrownBy(() -> new TrackingNumber(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
