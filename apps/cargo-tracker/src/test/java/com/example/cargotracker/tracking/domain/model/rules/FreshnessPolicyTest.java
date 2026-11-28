package com.example.cargotracker.tracking.domain.model.rules;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 鮮度の判定（FRS-02。Bolt 39g4 の状態の組み合わせの行 7・8）。次の予定の実績の予定時刻は 2026-11-01 00:00 UTC、許容範囲は 36 時間。判定の時刻が
 * 予定時刻 + 36 時間以上なら鮮度超過（ちょうどは超過。経路設計の情報鮮度の判定の「同時刻は足りる」と逆）。
 */
class FreshnessPolicyTest {

    private static final Optional<UtcInstant> EXPECTED = Optional.of(at("2026-11-01T00:00:00Z"));

    private final FreshnessPolicy policy = new FreshnessPolicy(Duration.ofHours(36));

    @ParameterizedTest(name = "行7 {0}")
    @CsvSource({
        "(a) 予定時刻より前, 2026-10-31T23:00:00Z, false",
        "(b) 予定時刻 + 35 時間 59 分 59 秒, 2026-11-02T11:59:59Z, false",
        "(c) 予定時刻 + 36 時間ちょうど, 2026-11-02T12:00:00Z, true",
        "(d) 予定時刻 + 48 時間, 2026-11-03T00:00:00Z, true"
    })
    void Bolt39g4_行7_判定の時刻が予定時刻と許容範囲の和以上なら鮮度超過(String row, String judgedAt, boolean exceeded) {
        assertThat(policy.exceeded(EXPECTED, at(judgedAt))).isEqualTo(exceeded);
    }

    @Test
    void Bolt39g4_行7e_次の予定の実績がなければ判定しない() {
        assertThat(policy.exceeded(Optional.empty(), at("2026-12-01T00:00:00Z")))
                .isFalse();
    }

    @Test
    void Bolt39g4_行8_許容範囲を持つ() {
        assertThat(policy.tolerance()).isEqualTo(Duration.ofHours(36));
    }

    @ParameterizedTest(name = "行8 {0}")
    @ValueSource(strings = {"PT0S", "-PT1H"})
    void Bolt39g4_行8_許容範囲が正でなければ組み立てで拒む(String tolerance) {
        Duration value = Duration.parse(tolerance);

        assertThatThrownBy(() -> new FreshnessPolicy(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("許容範囲");
    }
}
