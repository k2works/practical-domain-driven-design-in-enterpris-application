package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** KPI 計測記録の一覧の表示（期間は時間と分で分未満は切り捨て。日時は日時表示の共通部品。Bolt 21）。 */
class KpiObservationViewTest {

    @ParameterizedTest
    @CsvSource({
        "PT0S, 0 時間 0 分",
        "PT59S, 0 時間 0 分",
        "PT1M, 0 時間 1 分",
        "PT59M59S, 0 時間 59 分",
        "PT1H, 1 時間 0 分",
        "PT3H30M, 3 時間 30 分",
        "PT26H30M59S, 26 時間 30 分",
        "PT240H, 240 時間 0 分"
    })
    void 期間を時間と分で示し分未満は切り捨てる(String duration, String expected) {
        assertThat(KpiObservationView.formatDuration(Duration.parse(duration))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "2026-10-05T14:59:00Z | 2026-10-05 23:59 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 14:59）",
                "2026-10-05T15:00:00Z | 2026-10-06 00:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 15:00）"
            })
    void 日時は日本時間を主にしUTCを併記し日付をまたいでもそれぞれの日付を示す(String instant, String expected) {
        assertThat(KpiObservationView.staffDateTime(new UtcInstant(Instant.parse(instant))))
                .isEqualTo(expected);
    }
}
