package com.example.cargotracker.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 日時表示の共通部品（UI 設計）。見積り・経路設計・アクセス監査の写しを集めた（Bolt 22、#41）。 */
class DateTimeDisplayTest {

    @Test
    void 表示のタイムゾーンは日本時間に固定する() {
        assertThat(DateTimeDisplay.ZONE).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "2026-10-05T01:00:00Z | 2026-10-05 10:00 Asia/Tokyo（UTC+09:00）",
                "2026-10-05T14:59:00Z | 2026-10-05 23:59 Asia/Tokyo（UTC+09:00）",
                "2026-10-05T15:00:00Z | 2026-10-06 00:00 Asia/Tokyo（UTC+09:00）",
                "2026-10-05T01:00:59Z | 2026-10-05 10:00 Asia/Tokyo（UTC+09:00）"
            })
    void 荷主の画面は利用者のタイムゾーンで示す(String instant, String expected) {
        assertThat(DateTimeDisplay.customer(Instant.parse(instant))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "2026-10-05T01:00:00Z | 2026-10-05 10:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 01:00）",
                "2026-10-05T14:59:00Z | 2026-10-05 23:59 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 14:59）",
                "2026-10-05T15:00:00Z | 2026-10-06 00:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 15:00）",
                "2026-12-31T15:00:00Z | 2027-01-01 00:00 Asia/Tokyo（UTC+09:00）（UTC 2026-12-31 15:00）"
            })
    void 社内の画面は利用者のタイムゾーンを主にしUTCを併記し日付をまたいでもそれぞれの日付を示す(String instant, String expected) {
        assertThat(DateTimeDisplay.staff(Instant.parse(instant))).isEqualTo(expected);
    }
}
