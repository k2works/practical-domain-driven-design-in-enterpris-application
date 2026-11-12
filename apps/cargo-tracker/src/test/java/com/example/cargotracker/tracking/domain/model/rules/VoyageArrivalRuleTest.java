package com.example.cargotracker.tracking.domain.model.rules;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.leg;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyagePortCall;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 航海の到着予定の規則（Bolt 39g4b の行 2。【業務の判断】(au)、台帳 D-137）。予定は 2 区間（区間 2: V200 で KRPUS から USLAX、到着予定 A2 =
 * 2026-11-15 00:00）。積地は最後の区間の積地の港で出発のある寄港、揚地は最後の区間の揚地の港で到着のある寄港として読み、積地がちょうど 1 つで、その後ろの
 * 最初の揚地の到着を結ぶ。入力と期待の組まで縛る（T-244）。
 */
class VoyageArrivalRuleTest {

    private static final String A2_PLUS_10 = "2026-11-15T10:00:00Z";
    private static final String SOME = "2026-11-10T00:00:00Z";

    private final VoyageArrivalRule rule = new VoyageArrivalRule();

    static Stream<Arguments> 行2() {
        return Stream.of(
                Arguments.of(
                        "(a) KRPUS（出発）→ USLAX（到着）",
                        List.of(call("KRPUS", null, SOME), call("USLAX", A2_PLUS_10, null)),
                        A2_PLUS_10),
                Arguments.of(
                        "(b) 間に CNSHA",
                        List.of(call("KRPUS", null, SOME), call("CNSHA", SOME, SOME), call("USLAX", A2_PLUS_10, null)),
                        A2_PLUS_10),
                Arguments.of("(c) USLAX がない", List.of(call("KRPUS", null, SOME), call("CNSHA", SOME, null)), null),
                Arguments.of(
                        "(d) 積地の後に揚地がない",
                        List.of(call("USLAX", "2026-11-14T04:00:00Z", SOME), call("KRPUS", SOME, SOME)),
                        null),
                Arguments.of(
                        "(e) 出発のある積地が 2 つ（周回の航路）",
                        List.of(
                                call("KRPUS", null, SOME),
                                call("USLAX", A2_PLUS_10, SOME),
                                call("KRPUS", SOME, SOME),
                                call("USLAX", "2026-11-16T06:00:00Z", null)),
                        null),
                Arguments.of(
                        "(f) 積地 1 つの後に揚地が 2 つなら最初",
                        List.of(
                                call("KRPUS", null, SOME),
                                call("USLAX", A2_PLUS_10, SOME),
                                call("JPTYO", SOME, SOME),
                                call("USLAX", "2026-11-16T16:00:00Z", null)),
                        A2_PLUS_10),
                Arguments.of("(g) USLAX に到着がない", List.of(call("KRPUS", null, SOME), call("USLAX", null, null)), null),
                Arguments.of(
                        "(h) 最初の USLAX に到着がなければ次の USLAX",
                        List.of(
                                call("KRPUS", null, SOME),
                                call("USLAX", null, SOME),
                                call("USLAX", "2026-11-15T12:00:00Z", null)),
                        "2026-11-15T12:00:00Z"),
                Arguments.of("(i) 積地に出発がない", List.of(call("KRPUS", SOME, null), call("USLAX", A2_PLUS_10, null)), null),
                Arguments.of("(j) 積地がない", List.of(call("CNSHA", null, SOME), call("USLAX", A2_PLUS_10, null)), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void 行2(String row, List<VoyagePortCall> portCalls, String expected) {
        assertThat(rule.bind(schedule(), portCalls))
                .isEqualTo(java.util.Optional.ofNullable(expected)
                        .map(com.example.cargotracker.tracking.domain.model.TrackingFixture::at));
    }

    @Test
    void 行2k_1区間の予定はその区間の揚地への到着() {
        Schedule oneLeg = new Schedule(
                "RC-2026-0001",
                1,
                List.of(leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z")));

        assertThat(rule.bind(oneLeg, List.of(call("JPTYO", null, SOME), call("KRPUS", "2026-11-03T05:00:00Z", null))))
                .contains(at("2026-11-03T05:00:00Z"));
    }

    private static VoyagePortCall call(String port, String arrivalAt, String departureAt) {
        return VoyagePortCall.of(
                new Location(port),
                arrivalAt == null ? null : at(arrivalAt),
                departureAt == null ? null : at(departureAt));
    }
}
