package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CargoTest {

    @Test
    void 個数が1以上で総重量と容積が0より大きく小数点以下3桁までなら作れる() {
        assertThatCode(() -> new Cargo(
                        CargoCategory.GENERAL,
                        PackageType.PALLET,
                        1,
                        new BigDecimal("0.001"),
                        new BigDecimal("32.500")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"0, 8400, 32.5", "12, 0, 32.5", "12, 8400, 0", "12, -1, 32.5", "12, 8400.1234, 32.5"})
    void 個数と総重量と容積が範囲の外なら作れない(int packageCount, String grossWeightKg, String volumeM3) {
        assertThatThrownBy(() -> new Cargo(
                        CargoCategory.GENERAL,
                        PackageType.PALLET,
                        packageCount,
                        new BigDecimal(grossWeightKg),
                        new BigDecimal(volumeM3)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
