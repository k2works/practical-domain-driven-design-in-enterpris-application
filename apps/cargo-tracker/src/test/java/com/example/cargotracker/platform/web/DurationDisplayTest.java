package com.example.cargotracker.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 期間表示の 3 つの形（UI 設計の共通部品「日時表示」）。見積り・経路設計・アクセス監査の写しを、振る舞いを変えずに集めた
 * （Bolt 22、#41）。形をそろえるかは US-21 の残り（W9）で決める（D-84）。
 */
class DurationDisplayTest {

    /** 受付一覧（S-02）の待ち時間。1 日以上は日と時間、1 時間以上は時間と分、それ未満は分。負は 0 分。 */
    @ParameterizedTest
    @CsvSource({
        "-PT5M, 0 分",
        "PT0S, 0 分",
        "PT59S, 0 分",
        "PT59M, 59 分",
        "PT1H, 1 時間 0 分",
        "PT3H20M, 3 時間 20 分",
        "PT23H59M, 23 時間 59 分",
        "PT24H, 1 日 0 時間",
        "PT52H10M, 2 日 4 時間",
        "PT1H59S, 1 時間 0 分",
        "PT24H59M, 1 日 0 時間"
    })
    void 待ち時間(String duration, String expected) {
        assertThat(DurationDisplay.waiting(Duration.parse(duration))).isEqualTo(expected);
    }

    /**
     * 経路設計（S-06）の接続時間・接続余裕。日・時間・分のうち 0 でない部分を並べ、すべて 0 なら 0 分。
     * 負の期間は呼び出し側が反転して渡す（接続余裕の「不足」）。負をそのまま渡したときのいまの値も特性として固定する（Bolt 22 レビュー）。
     */
    @ParameterizedTest
    @CsvSource({
        "PT0S, 0 分",
        "PT59S, 0 分",
        "PT30M, 30 分",
        "PT4H, 4 時間",
        "PT4H30M, 4 時間 30 分",
        "PT24H, 1 日",
        "PT24H30M, 1 日 30 分",
        "PT36H, 1 日 12 時間",
        "PT49H5M, 2 日 1 時間 5 分",
        "PT1M, 1 分",
        "PT24H59S, 1 日",
        "-PT1H30M, -30 分"
    })
    void 接続時間(String duration, String expected) {
        assertThat(DurationDisplay.connection(Duration.parse(duration))).isEqualTo(expected);
    }

    /**
     * KPI（S-22）のリードタイム・経過時間。時間と分で示し、日に繰り上げず、分未満は切り捨てる（Bolt 21）。
     * 負の期間（時刻のずれ）のいまの値も特性として固定する。0 に丸めるかは W9 で決める（Bolt 22 レビュー）。
     */
    @ParameterizedTest
    @CsvSource({
        "PT0S, 0 時間 0 分",
        "PT59S, 0 時間 0 分",
        "PT1M, 0 時間 1 分",
        "PT59M59S, 0 時間 59 分",
        "PT1H, 1 時間 0 分",
        "PT3H30M, 3 時間 30 分",
        "PT26H30M59S, 26 時間 30 分",
        "PT240H, 240 時間 0 分",
        "PT48H, 48 時間 0 分",
        "-PT90M, -1 時間 -30 分"
    })
    void 時間と分(String duration, String expected) {
        assertThat(DurationDisplay.hoursAndMinutes(Duration.parse(duration))).isEqualTo(expected);
    }
}
