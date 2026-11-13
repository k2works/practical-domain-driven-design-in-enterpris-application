package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 営業日カレンダー。Bolt 40-4（SC-INV-04）で作った。日ごとの営業日・営業時間を持ち、受領・回答期限を数えるための営業時間の計算を提供する。登録のない日にかかる計算は
 * {@link BusinessCalendarNotRegisteredException} で失敗する（SVC-07）。すべての日は同じ時間帯を持つ。営業時間は開始以上、終了未満。
 *
 * @param days 日ごとの営業日・営業時間
 * @param zone 時間帯
 */
@ValueObject
public record BusinessCalendar(Map<LocalDate, BusinessDay> days, ZoneId zone) {

    public BusinessCalendar {
        Objects.requireNonNull(zone, "zone");
        days = Map.copyOf(Objects.requireNonNull(days, "days"));
    }

    /**
     * 日の一覧からカレンダーを作る。同じ日の二重登録と、時間帯の食い違いは拒む。
     *
     * @throws IllegalArgumentException 一覧が空、同じ日が二重、時間帯が食い違う
     */
    public static BusinessCalendar of(Collection<BusinessDay> days) {
        Objects.requireNonNull(days, "days");
        if (days.isEmpty()) {
            throw new IllegalArgumentException("営業日カレンダーは 1 日以上を持つ");
        }
        ZoneId zone = days.iterator().next().timeZone();
        Map<LocalDate, BusinessDay> byDate = new HashMap<>();
        for (BusinessDay day : days) {
            if (!day.timeZone().equals(zone)) {
                throw new IllegalArgumentException("営業日カレンダーの時間帯が食い違う: " + day.date());
            }
            if (byDate.put(day.date(), day) != null) {
                throw new IllegalArgumentException("同じ日が二重に登録されている: " + day.date());
            }
        }
        return new BusinessCalendar(byDate, zone);
    }

    /** 営業時間内なら受け取った時刻、外なら次に開く時刻。 */
    public Instant openingOnOrAfter(Instant at) {
        ZonedDateTime local = at.atZone(zone);
        LocalDate date = local.toLocalDate();
        BusinessDay day = dayOf(date);
        if (day.businessDay()) {
            LocalTime time = local.toLocalTime();
            if (time.isBefore(day.openTime())) {
                return instantOf(date, day.openTime());
            }
            if (time.isBefore(day.closeTime())) {
                return at;
            }
        }
        return nextOpeningAfter(date);
    }

    /**
     * 営業時間を数えて進めた時刻（営業時間外から始めるときは、次に開く時刻から数える）。終了ちょうどに届くときは終了の時刻を返す。
     *
     * @param from 数え始め
     * @param amount 営業時間の長さ
     */
    public Instant addBusinessHours(Instant from, Duration amount) {
        Instant current = openingOnOrAfter(from);
        Duration remaining = amount;
        while (true) {
            LocalDate date = current.atZone(zone).toLocalDate();
            Instant close = instantOf(date, dayOf(date).closeTime());
            Duration available = Duration.between(current, close);
            if (remaining.compareTo(available) <= 0) {
                return current.plus(remaining);
            }
            remaining = remaining.minus(available);
            current = nextOpeningAfter(date);
        }
    }

    /** その時刻を含む営業日の終了の時刻（営業時間内の時刻を受ける）。 */
    public Instant closeOfDayContaining(Instant at) {
        LocalDate date = at.atZone(zone).toLocalDate();
        return instantOf(date, dayOf(date).closeTime());
    }

    /**
     * 営業日を数えて進めた時刻。同じ時刻（その日の営業時間の中に丸める）の営業日 {@code days} 日後。
     *
     * @param from 数え始め（営業時間内）
     * @param days 営業日の数
     */
    public Instant addBusinessDays(Instant from, int days) {
        ZonedDateTime local = from.atZone(zone);
        LocalDate date = local.toLocalDate();
        for (int i = 0; i < days; i++) {
            date = nextBusinessDate(date);
        }
        BusinessDay day = dayOf(date);
        LocalTime time = local.toLocalTime();
        if (time.isBefore(day.openTime())) {
            time = day.openTime();
        } else if (time.isAfter(day.closeTime())) {
            time = day.closeTime();
        }
        return instantOf(date, time);
    }

    private BusinessDay dayOf(LocalDate date) {
        BusinessDay day = days.get(date);
        if (day == null) {
            throw new BusinessCalendarNotRegisteredException(date);
        }
        return day;
    }

    private LocalDate nextBusinessDate(LocalDate after) {
        LocalDate date = after.plusDays(1);
        while (!dayOf(date).businessDay()) {
            date = date.plusDays(1);
        }
        return date;
    }

    private Instant nextOpeningAfter(LocalDate after) {
        LocalDate date = nextBusinessDate(after);
        return instantOf(date, dayOf(date).openTime());
    }

    private Instant instantOf(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }
}
