package com.example.cargotracker.tracking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 追跡の追跡番号（追跡のコンテキスト固有の値。表記は予約が発行したもの。Bolt 25）。 */
class TrackingNumberTest {

    @Test
    void 予約が発行した形の追跡番号を受け取る() {
        assertThat(new TrackingNumber("CTABCDEFGH2345").value()).isEqualTo("CTABCDEFGH2345");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CTABCDEFGH234", "XXABCDEFGH2345", "CTABCDEFGH234O", "CTabcdefgh2345"})
    void 形式の違う追跡番号は受け取らない(String value) {
        assertThatThrownBy(() -> new TrackingNumber(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
