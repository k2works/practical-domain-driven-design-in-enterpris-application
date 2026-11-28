package com.example.cargotracker.quotation.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MvpAcceptancePolicyTest {

    private final MvpAcceptancePolicy policy = new MvpAcceptancePolicy();

    @Test
    void 一般の貨物を受け付ける() {
        assertThat(policy.accepts(CargoCategory.GENERAL)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = CargoCategory.class, names = "GENERAL", mode = EnumSource.Mode.EXCLUDE)
    void 一般以外の貨物は受け付けない(CargoCategory category) {
        assertThat(policy.accepts(category)).isFalse();
    }
}
