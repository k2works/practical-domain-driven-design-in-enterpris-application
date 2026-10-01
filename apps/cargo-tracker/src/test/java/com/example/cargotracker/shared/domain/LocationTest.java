package com.example.cargotracker.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LocationTest {

    @Test
    void UN_LOCODEの形式の場所を作れる() {
        assertThat(new Location("JPTYO").unLocode()).isEqualTo("JPTYO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"US2NY", "JPTY9"})
    void 地点コードには2から9の数字を使える(String unLocode) {
        assertThat(new Location(unLocode).unLocode()).isEqualTo(unLocode);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "TYO", "JPTYOO", "jptyo", "JP-TY", "JPTY0", "JPTY1", "J1TYO", " JPTYO"})
    void UN_LOCODEの形式でなければ場所を作れない(String unLocode) {
        assertThatThrownBy(() -> new Location(unLocode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UN/LOCODE");
    }
}
