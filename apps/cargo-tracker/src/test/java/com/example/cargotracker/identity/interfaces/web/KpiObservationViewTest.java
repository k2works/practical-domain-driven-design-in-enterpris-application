package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** KPI-01 リードタイムの表示（時間と分。分未満は切り捨て。Bolt 21 の確認ポイント 3）。 */
class KpiObservationViewTest {

    @ParameterizedTest
    @CsvSource({
        "PT0S, 0 時間 0 分",
        "PT59S, 0 時間 0 分",
        "PT1M, 0 時間 1 分",
        "PT3H30M, 3 時間 30 分",
        "PT26H30M59S, 26 時間 30 分",
        "PT240H, 240 時間 0 分"
    })
    void リードタイムを時間と分で示し分未満は切り捨てる(String duration, String expected) {
        assertThat(KpiObservationView.leadTime(Duration.parse(duration))).isEqualTo(expected);
    }
}
