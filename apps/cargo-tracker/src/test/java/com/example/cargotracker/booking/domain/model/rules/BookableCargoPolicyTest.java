package com.example.cargotracker.booking.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 本予約を確定できる貨物（B-INV-04、US-04 AC5。Bolt 35）。予約は見積りから写した貨物種別の名前だけで判定する。 */
class BookableCargoPolicyTest {

    @Test
    void 一般貨物は確定できる() {
        assertThat(BookableCargoPolicy.bookable("GENERAL")).isTrue();
    }

    @ParameterizedTest(name = "{0} は確定できない")
    @ValueSource(strings = {"DANGEROUS", "REEFER", "OTHER_SPECIAL"})
    void 特殊貨物は確定できない(String category) {
        assertThat(BookableCargoPolicy.bookable(category)).isFalse();
    }

    @ParameterizedTest(name = "「{0}」は確定しない側に倒す")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "general", "UNKNOWN"})
    void 知らない値と空の値は確定できない(String category) {
        assertThat(BookableCargoPolicy.bookable(category)).isFalse();
    }
}
