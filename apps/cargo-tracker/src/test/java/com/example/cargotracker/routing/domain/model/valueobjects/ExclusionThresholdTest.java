package com.example.cargotracker.routing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.ArrivalDeadline;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.ConnectionRuleMissing;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.MinimumConnection;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.StaleVoyageInfo;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.UnsupportedCargo;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 除外の閾値（R-INV-02）。区分ごとの型と、区分と閾値の 1 対 1（Bolt 39i の行 20〜22）。 */
class ExclusionThresholdTest {

    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-11-03T12:00:00Z"));
    private static final Location SINGAPORE = new Location("SGSIN");

    @Nested
    class Bolt39i_区分と閾値の組 {

        static Stream<Arguments> 五区分() {
            return Stream.of(
                    Arguments.of(
                            ExclusionReasonCode.DEADLINE_EXCEEDED, ExclusionReason.deadlineExceeded(AT, AT, "V-1@1")),
                    Arguments.of(
                            ExclusionReasonCode.CONNECTION_TOO_SHORT,
                            ExclusionReason.connectionTooShort(AT, SINGAPORE, Duration.ofHours(8), "V-1@1")),
                    Arguments.of(
                            ExclusionReasonCode.NOT_CONNECTABLE,
                            ExclusionReason.notConnectable(AT, SINGAPORE, "V-1@1")),
                    Arguments.of(
                            ExclusionReasonCode.INFO_INSUFFICIENT,
                            ExclusionReason.infoInsufficient("IT STALE 1", AT, Duration.ofHours(24), "V-1@1")),
                    Arguments.of(
                            ExclusionReasonCode.CARGO_NOT_SUPPORTED,
                            ExclusionReason.cargoNotSupported(RouteCargoCategory.REEFER, AT, "V-1@1")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("五区分")
        void 区分の値と理由の区分が一対一で閾値の型が決める(ExclusionReasonCode expected, ExclusionReason reason) {
            assertThat(reason.code()).isEqualTo(expected);
            assertThat(reason.threshold().code()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("型と区分")
        void 閾値の型ごとに区分が違う(ExclusionThreshold threshold, ExclusionReasonCode expected) {
            assertThat(threshold.code()).isEqualTo(expected);
        }

        static Stream<Arguments> 型と区分() {
            return Stream.of(
                    Arguments.of(new ArrivalDeadline(AT), ExclusionReasonCode.DEADLINE_EXCEEDED),
                    Arguments.of(
                            new MinimumConnection(SINGAPORE, Duration.ofHours(8)),
                            ExclusionReasonCode.CONNECTION_TOO_SHORT),
                    Arguments.of(new ConnectionRuleMissing(SINGAPORE), ExclusionReasonCode.NOT_CONNECTABLE),
                    Arguments.of(
                            new StaleVoyageInfo("V-1", Duration.ofHours(24)), ExclusionReasonCode.INFO_INSUFFICIENT),
                    Arguments.of(
                            new UnsupportedCargo(RouteCargoCategory.DANGEROUS),
                            ExclusionReasonCode.CARGO_NOT_SUPPORTED));
        }
    }

    @Nested
    class Bolt39i_誤った入力 {

        @ParameterizedTest(name = "{0}")
        @MethodSource("理由のnull")
        void 理由の閾値と時刻と参照情報版がnullなら作れない(String name, Supplier<ExclusionReason> create) {
            assertThatThrownBy(create::get).isInstanceOf(NullPointerException.class);
        }

        static Stream<Arguments> 理由のnull() {
            ExclusionThreshold threshold = new ConnectionRuleMissing(SINGAPORE);
            return Stream.of(
                    Arguments.of("閾値", (Supplier<ExclusionReason>) () -> new ExclusionReason(null, AT, "V-1@1")),
                    Arguments.of(
                            "不適合の時刻", (Supplier<ExclusionReason>) () -> new ExclusionReason(threshold, null, "V-1@1")),
                    Arguments.of("参照情報版", (Supplier<ExclusionReason>) () -> new ExclusionReason(threshold, AT, null)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("閾値のnull")
        void 閾値の値がnullなら作れない(String name, Supplier<ExclusionThreshold> create) {
            assertThatThrownBy(create::get).isInstanceOf(NullPointerException.class);
        }

        static Stream<Arguments> 閾値のnull() {
            return Stream.of(
                    Arguments.of("期限なし", (Supplier<ExclusionThreshold>) () -> new ArrivalDeadline(null)),
                    Arguments.of("港なし", (Supplier<ExclusionThreshold>) () -> new ConnectionRuleMissing(null)),
                    Arguments.of("接続時間なし", (Supplier<ExclusionThreshold>) () -> new MinimumConnection(SINGAPORE, null)),
                    Arguments.of("航海番号なし", (Supplier<ExclusionThreshold>)
                            () -> new StaleVoyageInfo(null, Duration.ofHours(24))),
                    Arguments.of("許容時間なし", (Supplier<ExclusionThreshold>) () -> new StaleVoyageInfo("V-1", null)),
                    Arguments.of("種別なし", (Supplier<ExclusionThreshold>) () -> new UnsupportedCargo(null)));
        }

        @Test
        void 航海番号が空白だけなら作れない() {
            assertThatThrownBy(() -> new StaleVoyageInfo(" ", Duration.ofHours(24)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
