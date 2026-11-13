package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 営業日カレンダーの 1 日（表 {@code tracking.business_calendar} の 1 行。Bolt 40-4）。営業日は開始と終了を持ち（開始 &lt; 終了）、休業日は持たない。
 * 営業時間は開始以上、終了未満（終了ちょうどは営業時間外）。
 *
 * @param date 日付（現地）
 * @param businessDay 営業日か
 * @param openTime 開始（営業日だけ）
 * @param closeTime 終了（営業日だけ）
 * @param timeZone 時間帯
 * @param note 備考（祝日の名前など。なければ {@code null}）
 */
@ValueObject
public record BusinessDay(
        LocalDate date, boolean businessDay, LocalTime openTime, LocalTime closeTime, ZoneId timeZone, String note) {

    public BusinessDay {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(timeZone, "timeZone");
        if (businessDay) {
            if (openTime == null || closeTime == null || !openTime.isBefore(closeTime)) {
                throw new IllegalArgumentException("営業日は開始が終了より前: " + date);
            }
        } else if (openTime != null || closeTime != null) {
            throw new IllegalArgumentException("休業日は営業時間を持たない: " + date);
        }
    }

    /** 営業日。 */
    public static BusinessDay open(
            LocalDate date, LocalTime openTime, LocalTime closeTime, ZoneId timeZone, String note) {
        return new BusinessDay(date, true, openTime, closeTime, timeZone, note);
    }

    /** 休業日。 */
    public static BusinessDay closed(LocalDate date, ZoneId timeZone, String note) {
        return new BusinessDay(date, false, null, null, timeZone, note);
    }
}
