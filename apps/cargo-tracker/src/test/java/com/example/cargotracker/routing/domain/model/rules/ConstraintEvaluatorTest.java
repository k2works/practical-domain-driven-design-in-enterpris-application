package com.example.cargotracker.routing.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 制約適合判定の境界（BR-11、R-INV-01・02、US-06 AC2・AC3）。境界は直前・同時刻・直後の 3 点で書く（T-38）。
 * このテストの表は、Golden dataset（TST-02、W6）が届くまでの BR-11 の仕様である（ステップ 2 の承認ゲート）。
 */
class ConstraintEvaluatorTest {

    static final Location TOKYO = new Location("JPTYO");
    static final Location SINGAPORE = new Location("SGSIN");
    static final Location ROTTERDAM = new Location("NLRTM");
    static final Location HONG_KONG = new Location("HKHKG");
    /** 希望到着期限 2026-11-02 09:00 JST。 */
    static final Instant DEADLINE = Instant.parse("2026-11-02T00:00:00Z");

    static final UtcInstant JUDGED_AT = at("2026-10-07T03:00:00Z");
    static final RouteSpecification SPEC =
            new RouteSpecification(TOKYO, ROTTERDAM, new UtcInstant(DEADLINE), "GENERAL");

    private final ConstraintEvaluator evaluator = new ConstraintEvaluator();

    @Nested
    class 期限 {

        @ParameterizedTest(name = "到着予定が期限の {0} 分後なら適合={1}")
        @CsvSource({"-1, true", "0, true", "1, false"})
        void 到着予定が期限以前なら適合し後なら期限超過で除外する(long minutesAfterDeadline, boolean conforming) {
            Instant arrival = DEADLINE.plus(Duration.ofMinutes(minutesAfterDeadline));
            Leg direct = leg("V-101", TOKYO, ROTTERDAM, "2026-10-08T00:00:00Z", arrival, "V-101@1");

            ConstraintEvaluation evaluation = evaluator.evaluate(SPEC, List.of(direct), List.of(), JUDGED_AT);

            assertThat(evaluation.conforming()).isEqualTo(conforming);
            assertThat(evaluation.estimatedArrivalAt()).isEqualTo(new UtcInstant(arrival));
            if (!conforming) {
                assertThat(evaluation.reasons())
                        .containsExactly(ExclusionReason.deadlineExceeded(
                                new UtcInstant(arrival), new UtcInstant(DEADLINE), "V-101@1"));
            }
        }

        @Test
        void 期限超過の参照情報版は最終区間の航海の採用情報版() {
            List<Leg> legs = List.of(
                    leg("V-201", TOKYO, SINGAPORE, "2026-10-10T00:00:00Z", "2026-10-20T00:00:00Z", "V-201@3"),
                    leg("V-301", SINGAPORE, ROTTERDAM, "2026-10-21T00:00:00Z", "2026-11-03T12:00:00Z", "V-301@5"));

            ConstraintEvaluation evaluation =
                    evaluator.evaluate(SPEC, legs, List.of(rule(SINGAPORE, Duration.ofHours(8))), JUDGED_AT);

            assertThat(evaluation.reasons())
                    .containsExactly(ExclusionReason.deadlineExceeded(
                            at("2026-11-03T12:00:00Z"), new UtcInstant(DEADLINE), "V-301@5"));
        }

        @Test
        void 直行の候補は接続余裕を持たない() {
            Leg direct = leg("V-101", TOKYO, ROTTERDAM, "2026-10-08T00:00:00Z", "2026-10-30T09:00:00Z", "V-101@1");

            assertThat(evaluator
                            .evaluate(SPEC, List.of(direct), List.of(), JUDGED_AT)
                            .connectionSlack())
                    .isEmpty();
        }
    }

    @Nested
    class 接続時間 {

        static final Instant ARRIVAL_AT_SINGAPORE = Instant.parse("2026-10-20T00:00:00Z");

        @ParameterizedTest(name = "必要最小接続時間 {0} 時間に対し接続時間が {1} 分多ければ適合={2}")
        @CsvSource({
            "8, -1, false",
            "8, 0, true",
            "8, 1, true",
            // T-41: 既定（仮の値の 8 時間）と違う規則の値でも、同値が適合・1 分前が除外になる
            "12, -1, false",
            "12, 0, true",
            "12, 1, true"
        })
        void 接続時間が必要最小接続時間以上なら適合し未満なら接続不足で除外する(long requiredHours, long minutesOverRequired, boolean conforming) {
            Duration required = Duration.ofHours(requiredHours);
            Instant nextDeparture = ARRIVAL_AT_SINGAPORE.plus(required).plus(Duration.ofMinutes(minutesOverRequired));
            List<Leg> legs = transshipment(nextDeparture);

            ConstraintEvaluation evaluation =
                    evaluator.evaluate(SPEC, legs, List.of(rule(SINGAPORE, required)), JUDGED_AT);

            assertThat(evaluation.conforming()).isEqualTo(conforming);
            assertThat(evaluation.connectionSlack()).contains(Duration.ofMinutes(minutesOverRequired));
            if (!conforming) {
                assertThat(evaluation.reasons())
                        .containsExactly(ExclusionReason.connectionTooShort(
                                new UtcInstant(nextDeparture), SINGAPORE, required, "V-301@5"));
            }
        }

        @Test
        void 積替えの港に規則がなければ接続できないで除外する() {
            Instant nextDeparture = ARRIVAL_AT_SINGAPORE.plus(Duration.ofDays(1));

            ConstraintEvaluation evaluation = evaluator.evaluate(
                    SPEC, transshipment(nextDeparture), List.of(rule(HONG_KONG, Duration.ofHours(8))), JUDGED_AT);

            assertThat(evaluation.conforming()).isFalse();
            assertThat(evaluation.reasons())
                    .containsExactly(
                            ExclusionReason.notConnectable(new UtcInstant(nextDeparture), SINGAPORE, "V-301@5"));
            assertThat(evaluation.connectionSlack()).isEmpty();
        }

        @Test
        void 判定時刻で有効でない規則は使わない() {
            Instant nextDeparture = ARRIVAL_AT_SINGAPORE.plus(Duration.ofDays(1));
            ConnectionRule expired = new ConnectionRule(
                    UUID.randomUUID(), SINGAPORE, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), JUDGED_AT);
            ConnectionRule notYet = new ConnectionRule(
                    UUID.randomUUID(),
                    SINGAPORE,
                    Duration.ofHours(8),
                    new UtcInstant(JUDGED_AT.instant().plusSeconds(1)),
                    null);

            ConstraintEvaluation evaluation =
                    evaluator.evaluate(SPEC, transshipment(nextDeparture), List.of(expired, notYet), JUDGED_AT);

            assertThat(evaluation.reasons())
                    .extracting(ExclusionReason::code)
                    .containsExactly(ExclusionReasonCode.NOT_CONNECTABLE);
        }

        @Test
        void 同じ港に有効な規則が複数あれば厳しい方を使う() {
            Duration loose = Duration.ofHours(6);
            Duration strict = Duration.ofHours(10);
            Instant nextDeparture = ARRIVAL_AT_SINGAPORE.plus(Duration.ofHours(8));

            ConstraintEvaluation evaluation = evaluator.evaluate(
                    SPEC,
                    transshipment(nextDeparture),
                    List.of(rule(SINGAPORE, loose), rule(SINGAPORE, strict)),
                    JUDGED_AT);

            assertThat(evaluation.reasons())
                    .containsExactly(ExclusionReason.connectionTooShort(
                            new UtcInstant(nextDeparture), SINGAPORE, strict, "V-301@5"));
            assertThat(evaluation.connectionSlack()).contains(Duration.ofHours(-2));
        }

        private static List<Leg> transshipment(Instant nextDeparture) {
            return List.of(
                    leg("V-201", TOKYO, SINGAPORE, "2026-10-10T00:00:00Z", ARRIVAL_AT_SINGAPORE, "V-201@3"),
                    leg(
                            "V-301",
                            SINGAPORE,
                            ROTTERDAM,
                            nextDeparture,
                            Instant.parse("2026-10-30T00:00:00Z"),
                            "V-301@5"));
        }
    }

    @Test
    void 期限超過と接続不足の両方があれば理由を2つ持つ() {
        List<Leg> legs = List.of(
                leg("V-201", TOKYO, SINGAPORE, "2026-10-10T00:00:00Z", "2026-10-20T00:00:00Z", "V-201@3"),
                leg("V-301", SINGAPORE, ROTTERDAM, "2026-10-20T04:00:00Z", "2026-11-03T00:00:00Z", "V-301@5"));

        ConstraintEvaluation evaluation =
                evaluator.evaluate(SPEC, legs, List.of(rule(SINGAPORE, Duration.ofHours(8))), JUDGED_AT);

        assertThat(evaluation.reasons())
                .extracting(ExclusionReason::code)
                .containsExactly(ExclusionReasonCode.CONNECTION_TOO_SHORT, ExclusionReasonCode.DEADLINE_EXCEEDED);
    }

    static ConnectionRule rule(Location port, Duration minimum) {
        return new ConnectionRule(UUID.randomUUID(), port, minimum, at("2026-01-01T00:00:00Z"), null);
    }

    static Leg leg(String voyage, Location load, Location discharge, String departure, String arrival, String version) {
        return leg(voyage, load, discharge, Instant.parse(departure), Instant.parse(arrival), version);
    }

    static Leg leg(
            String voyage, Location load, Location discharge, String departure, Instant arrival, String version) {
        return leg(voyage, load, discharge, Instant.parse(departure), arrival, version);
    }

    static Leg leg(
            String voyage, Location load, Location discharge, Instant departure, Instant arrival, String version) {
        return new Leg(
                voyage,
                load,
                discharge,
                new UtcInstant(departure),
                new UtcInstant(arrival),
                version,
                at("2026-10-01T06:10:00Z"));
    }

    static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}
