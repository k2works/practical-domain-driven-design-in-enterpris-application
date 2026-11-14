package com.example.cargotracker.platform.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日時表示の共通部品（UI 設計）。UTC の時点を利用者のタイムゾーンで「年月日 時刻 タイムゾーン（UTC offset）」と示し、
 * 社内の画面では UTC を括弧で併記する（BR-10、R-30）。見積り・経路設計・アクセス監査の写しを集めた（Bolt 22、#41）。
 */
public final class DateTimeDisplay {

    /** 画面のタイムゾーン。利用者ごとの設定は後で入れる（BR-10）。入れるときに直す場所はここだけにする。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV（'UTC'xxx）");

    private static final DateTimeFormatter ZONE_LABEL = DateTimeFormatter.ofPattern("VV（'UTC'xxx）");

    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private DateTimeDisplay() {}

    /** 荷主の画面の日時（例: 2026-10-05 10:00 Asia/Tokyo（UTC+09:00））。 */
    public static String customer(Instant instant) {
        return DATE_TIME.format(ZonedDateTime.ofInstant(instant, ZONE));
    }

    /** 荷主の画面のタイムゾーンの表示（例: Asia/Tokyo（UTC+09:00））。時刻の後ろに付けて、時間帯とずれを示す（問い合わせの受付時間。Bolt 42-2）。 */
    public static String customerZoneLabel(Instant at) {
        return ZONE_LABEL.format(ZonedDateTime.ofInstant(at, ZONE));
    }

    /** 社内の画面の日時（例: 2026-10-05 10:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 01:00））。 */
    public static String staff(Instant instant) {
        return customer(instant) + "（UTC " + UTC_TIME.format(instant.atOffset(ZoneOffset.UTC)) + "）";
    }
}
