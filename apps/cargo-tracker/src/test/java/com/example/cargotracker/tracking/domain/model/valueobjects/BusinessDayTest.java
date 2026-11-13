package com.example.cargotracker.tracking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** 営業日カレンダーの 1 日の不変条件（Bolt 40-4。表 {@code business_calendar} の CHECK と同じ規則）。 */
class BusinessDayTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 16);

    @Test
    void 営業日は開始が終了より前でそろう() {
        BusinessDay day = BusinessDay.open(DATE, LocalTime.of(9, 0), LocalTime.of(18, 0), JST, null);

        assertThat(day.businessDay()).isTrue();
        assertThat(day.openTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void 営業日の開始が終了以降だと拒む() {
        assertThatThrownBy(() -> BusinessDay.open(DATE, LocalTime.of(18, 0), LocalTime.of(18, 0), JST, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 休業日は営業時間を持たない() {
        BusinessDay day = BusinessDay.closed(DATE, JST, "祝日");

        assertThat(day.businessDay()).isFalse();
        assertThat(day.openTime()).isNull();
        assertThat(day.closeTime()).isNull();
    }

    @Test
    void 営業日に営業時間がないと拒む() {
        assertThatThrownBy(() -> new BusinessDay(DATE, true, null, null, JST, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 休業日に営業時間があると拒む() {
        assertThatThrownBy(() -> new BusinessDay(DATE, false, LocalTime.of(9, 0), LocalTime.of(18, 0), JST, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 同じ日が二重に登録されたカレンダーは拒む() {
        BusinessDay day = BusinessDay.open(DATE, LocalTime.of(9, 0), LocalTime.of(18, 0), JST, null);

        assertThatThrownBy(() -> BusinessCalendar.of(java.util.List.of(day, day)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
