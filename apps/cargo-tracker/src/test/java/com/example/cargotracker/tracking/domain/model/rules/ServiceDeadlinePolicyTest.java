package com.example.cargotracker.tracking.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 受領・回答期限の計算（Bolt 40-4。SC-INV-04、SVC-01〜05。期限の値は提案で仮）。営業日は平日 09:00〜18:00 JST、2026-11-23（月）は祝日、
 * 2026-12-29〜2027-01-03 は休業。範囲は 2026-11-01〜2027-01-31。計画のデシジョンテーブルの行 1〜13。
 */
class ServiceDeadlinePolicyTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final ServiceDeadlinePolicy POLICY = ServiceDeadlinePolicy.provisional();

    private static Instant jst(String dateTime) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(dateTime), JST).toInstant();
    }

    static BusinessCalendar calendar() {
        Set<LocalDate> closed = Set.of(
                LocalDate.of(2026, 11, 23),
                LocalDate.of(2026, 12, 29),
                LocalDate.of(2026, 12, 30),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 2),
                LocalDate.of(2027, 1, 3));
        List<BusinessDay> days = new ArrayList<>();
        for (LocalDate date = LocalDate.of(2026, 11, 1);
                !date.isAfter(LocalDate.of(2027, 1, 31));
                date = date.plusDays(1)) {
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (weekend || closed.contains(date)) {
                days.add(BusinessDay.closed(date, JST, null));
            } else {
                days.add(BusinessDay.open(date, LocalTime.of(9, 0), LocalTime.of(18, 0), JST, null));
            }
        }
        return BusinessCalendar.of(days);
    }

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of(
                        "行1 高・営業時間内",
                        ServicePriority.HIGH,
                        "2026-11-16T10:00",
                        "2026-11-16T10:00",
                        "2026-11-16T11:00",
                        "2026-11-16T18:00"),
                Arguments.of(
                        "行2 高・営業時間をまたぐ",
                        ServicePriority.HIGH,
                        "2026-11-16T17:30",
                        "2026-11-16T17:30",
                        "2026-11-17T09:30",
                        "2026-11-16T18:00"),
                Arguments.of(
                        "行3 高・営業時間外",
                        ServicePriority.HIGH,
                        "2026-11-16T19:00",
                        "2026-11-17T09:00",
                        "2026-11-17T10:00",
                        "2026-11-17T18:00"),
                Arguments.of(
                        "行4 高・金曜の夜（月曜は祝日）",
                        ServicePriority.HIGH,
                        "2026-11-20T19:00",
                        "2026-11-24T09:00",
                        "2026-11-24T10:00",
                        "2026-11-24T18:00"),
                Arguments.of(
                        "行5 高・土曜",
                        ServicePriority.HIGH,
                        "2026-11-21T11:00",
                        "2026-11-24T09:00",
                        "2026-11-24T10:00",
                        "2026-11-24T18:00"),
                Arguments.of(
                        "行6 高・18:00 ちょうど",
                        ServicePriority.HIGH,
                        "2026-11-16T18:00",
                        "2026-11-17T09:00",
                        "2026-11-17T10:00",
                        "2026-11-17T18:00"),
                Arguments.of(
                        "行7 高・09:00 ちょうど",
                        ServicePriority.HIGH,
                        "2026-11-16T09:00",
                        "2026-11-16T09:00",
                        "2026-11-16T10:00",
                        "2026-11-16T18:00"),
                Arguments.of(
                        "行8 通常・営業時間内",
                        ServicePriority.NORMAL,
                        "2026-11-16T10:00",
                        "2026-11-16T10:00",
                        "2026-11-16T14:00",
                        "2026-11-18T10:00"),
                Arguments.of(
                        "行9 通常・営業時間をまたぐ",
                        ServicePriority.NORMAL,
                        "2026-11-16T15:00",
                        "2026-11-16T15:00",
                        "2026-11-17T10:00",
                        "2026-11-18T15:00"),
                Arguments.of(
                        "行10 通常・祝日をまたぐ",
                        ServicePriority.NORMAL,
                        "2026-11-19T10:00",
                        "2026-11-19T10:00",
                        "2026-11-19T14:00",
                        "2026-11-24T10:00"),
                Arguments.of(
                        "行11 通常・年末年始をまたぐ",
                        ServicePriority.NORMAL,
                        "2026-12-28T10:00",
                        "2026-12-28T10:00",
                        "2026-12-28T14:00",
                        "2027-01-05T10:00"),
                Arguments.of(
                        "行12 通常・営業時間外",
                        ServicePriority.NORMAL,
                        "2026-11-16T18:30",
                        "2026-11-17T09:00",
                        "2026-11-17T13:00",
                        "2026-11-19T09:00"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void デシジョンテーブル(
            String name,
            ServicePriority priority,
            String received,
            String countedFrom,
            String receiptDue,
            String responseDue) {
        ServiceDeadlines deadlines = POLICY.deadlinesFor(priority, jst(received), calendar());

        assertThat(deadlines.countedFrom()).isEqualTo(jst(countedFrom));
        assertThat(deadlines.receiptDue()).isEqualTo(jst(receiptDue));
        assertThat(deadlines.responseDue()).isEqualTo(jst(responseDue));
        // 次回更新日時は受領期限（営業時間外の優先度高は翌営業日 10:00。SVC-05）
        assertThat(deadlines.nextUpdateAt()).isEqualTo(deadlines.receiptDue());
    }

    @Test
    void 行13_カレンダーにない日にかかる計算は黙って平日にせず失敗する() {
        BusinessCalendar calendar = calendar();
        Instant afterRange = jst("2027-01-29T17:30");

        assertThatThrownBy(() -> POLICY.deadlinesFor(ServicePriority.NORMAL, afterRange, calendar))
                .isInstanceOf(BusinessCalendarNotRegisteredException.class)
                .hasMessageContaining("2027-02");
    }
}
