package com.example.cargotracker.tracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.tracking.domain.model.aggregates.BusinessCalendarRepository;
import com.example.cargotracker.tracking.domain.model.rules.ServiceDeadlinePolicy;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 営業日カレンダーの表 {@code tracking.business_calendar} と見本の移し替え（Bolt 40-4。PostgreSQL。H2 は dev プロファイルの起動で同じ移し替えが流れる）。
 * 見本は 2026・2027 年の全日で、日本の祝日・年末年始・土日を休業日とする。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisBusinessCalendarRepositoryIntegrationTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final LocalDate FIRST = LocalDate.of(2026, 1, 1);
    private static final LocalDate LAST = LocalDate.of(2027, 12, 31);

    @Autowired
    BusinessCalendarRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 見本は2026年と2027年の全日をそろえる() {
        BusinessCalendar calendar = repository.load(FIRST, LAST);

        assertThat(calendar.days()).hasSize(730);
        assertThat(calendar.zone()).isEqualTo(JST);
        assertThat(FIRST.datesUntil(LAST.plusDays(1)))
                .allSatisfy(date -> assertThat(calendar.days()).containsKey(date));
    }

    @Test
    void 見本は平日が営業日で土日と祝日と年末年始が休業日() {
        BusinessCalendar calendar = repository.load(FIRST, LAST);

        assertThat(calendar.days().values()).allSatisfy(day -> {
            boolean weekend = day.date().getDayOfWeek() == DayOfWeek.SATURDAY
                    || day.date().getDayOfWeek() == DayOfWeek.SUNDAY;
            if (day.businessDay()) {
                assertThat(weekend).as(day.date().toString()).isFalse();
                assertThat(day.openTime()).isEqualTo(LocalTime.of(9, 0));
                assertThat(day.closeTime()).isEqualTo(LocalTime.of(18, 0));
            }
        });
        assertThat(isClosed(calendar, LocalDate.of(2026, 11, 23))).isTrue(); // 勤労感謝の日
        assertThat(isClosed(calendar, LocalDate.of(2026, 5, 6))).isTrue(); // 振替休日
        assertThat(isClosed(calendar, LocalDate.of(2026, 9, 22))).isTrue(); // 国民の休日
        assertThat(isClosed(calendar, LocalDate.of(2027, 3, 22))).isTrue(); // 振替休日
        assertThat(isClosed(calendar, LocalDate.of(2026, 12, 29))).isTrue(); // 年末年始
        assertThat(isClosed(calendar, LocalDate.of(2027, 1, 3))).isTrue();
        assertThat(isClosed(calendar, LocalDate.of(2026, 11, 16))).isFalse();
        assertThat(isClosed(calendar, LocalDate.of(2027, 1, 4))).isFalse();
    }

    @Test
    void 見本の2026年11月の営業日は19日() {
        BusinessCalendar calendar = repository.load(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        // 平日 21 日から 11-03（文化の日）と 11-23（勤労感謝の日）を除く
        assertThat(calendar.days().values().stream().filter(BusinessDay::businessDay))
                .hasSize(19);
    }

    @Test
    void 読んだ見本で期限を計算できる() {
        BusinessCalendar calendar = repository.load(FIRST, LAST);
        ZonedDateTime received = ZonedDateTime.of(LocalDateTime.parse("2026-12-28T10:00"), JST);

        ServiceDeadlines deadlines = ServiceDeadlinePolicy.provisional()
                .deadlinesFor(ServicePriority.NORMAL, received.toInstant(), calendar);

        assertThat(deadlines.receiptDue())
                .isEqualTo(ZonedDateTime.of(LocalDateTime.parse("2026-12-28T14:00"), JST)
                        .toInstant());
        // 年末年始（12-29〜1-03）を飛ばして 1-04 が 1 営業日、1-05 が 2 営業日
        assertThat(deadlines.responseDue())
                .isEqualTo(ZonedDateTime.of(LocalDateTime.parse("2027-01-05T10:00"), JST)
                        .toInstant());
    }

    @Test
    void 登録のない期間を読むと登録漏れとして失敗する() {
        LocalDate from = LocalDate.of(2028, 1, 1);
        LocalDate to = LocalDate.of(2028, 1, 31);

        assertThatThrownBy(() -> repository.load(from, to)).isInstanceOf(BusinessCalendarNotRegisteredException.class);
    }

    @Test
    void 営業日に営業時間がない行は表の制約で拒む() {
        assertThatThrownBy(() -> insert("2030-01-07", true, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 開始が終了以降の営業日の行は表の制約で拒む() {
        assertThatThrownBy(() -> insert("2030-01-08", true, "18:00:00", "09:00:00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 休業日に営業時間がある行は表の制約で拒む() {
        assertThatThrownBy(() -> insert("2030-01-09", false, "09:00:00", "18:00:00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 同じ日を二重に登録できない() {
        assertThatThrownBy(() -> insert("2026-11-16", true, "09:00:00", "18:00:00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static boolean isClosed(BusinessCalendar calendar, LocalDate date) {
        return !calendar.days().get(date).businessDay();
    }

    private void insert(String date, boolean businessDay, String open, String close) {
        jdbc.update(
                "INSERT INTO tracking.business_calendar (calendar_date, business_day, open_time, close_time, time_zone)"
                        + " VALUES (CAST(? AS DATE), ?, CAST(? AS TIME), CAST(? AS TIME), 'Asia/Tokyo')",
                date,
                businessDay,
                open,
                close);
    }
}
