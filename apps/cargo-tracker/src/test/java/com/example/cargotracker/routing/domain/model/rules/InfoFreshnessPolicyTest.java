package com.example.cargotracker.routing.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 情報鮮度の判定（US-06 AC4、R-INV-09、D-89。Bolt 37b2）。判定時刻が「取得時刻 + 許容時間」より後なら情報不足（同時刻は足りる）。
 * 境界は直前・同時刻・直後の 3 点で書く（T-38）。取得時刻は判定時刻からの差で作る（T-171。既定の取得時刻の定数を写さない）。
 */
class InfoFreshnessPolicyTest {

    static final Location TOKYO = new Location("JPTYO");
    static final Location SINGAPORE = new Location("SGSIN");
    static final Location ROTTERDAM = new Location("NLRTM");
    static final Duration TOLERANCE = Duration.ofHours(24);
    static final Instant JUDGED_AT = Instant.parse("2026-10-07T03:00:00Z");

    private final InfoFreshnessPolicy policy = new InfoFreshnessPolicy(TOLERANCE);

    @ParameterizedTest(name = "取得が判定時刻の 24 時間と {0} 秒前なら情報不足={1}")
    @CsvSource({"1, true", "0, false", "-1, false"})
    void 取得時刻と許容時間の和より後に判定したら情報不足で同時刻は足りる(long secondsBeyond, boolean insufficient) {
        Instant acquiredAt = JUDGED_AT.minus(TOLERANCE).minusSeconds(secondsBeyond);
        Leg leg = leg("V-101", TOKYO, ROTTERDAM, "V-101@3", acquiredAt);

        List<ExclusionReason> reasons = policy.evaluate(List.of(leg), new UtcInstant(JUDGED_AT));

        assertThat(reasons)
                .isEqualTo(
                        insufficient
                                ? List.of(ExclusionReason.infoInsufficient(
                                        "V-101", new UtcInstant(acquiredAt), TOLERANCE, "V-101@3"))
                                : List.of());
    }

    @Test
    void 二つ目の区間の航海だけが古ければその航海の理由だけを返す() {
        Instant fresh = JUDGED_AT.minus(Duration.ofHours(2));
        Instant stale = JUDGED_AT.minus(Duration.ofHours(30));
        List<Leg> legs = List.of(
                leg("V-201", TOKYO, SINGAPORE, "V-201@1", fresh), leg("V-301", SINGAPORE, ROTTERDAM, "V-301@2", stale));

        List<ExclusionReason> reasons = policy.evaluate(legs, new UtcInstant(JUDGED_AT));

        assertThat(reasons)
                .containsExactly(
                        ExclusionReason.infoInsufficient("V-301", new UtcInstant(stale), TOLERANCE, "V-301@2"));
    }

    @Test
    void 二つの航海が古ければ取得時刻の古さではなく区間の順に理由を返す() {
        Instant older = JUDGED_AT.minus(Duration.ofHours(50));
        Instant old = JUDGED_AT.minus(Duration.ofHours(30));
        List<Leg> legs = List.of(
                leg("V-201", TOKYO, SINGAPORE, "V-201@1", old), leg("V-301", SINGAPORE, ROTTERDAM, "V-301@2", older));

        List<ExclusionReason> reasons = policy.evaluate(legs, new UtcInstant(JUDGED_AT));

        assertThat(reasons)
                .containsExactly(
                        ExclusionReason.infoInsufficient("V-201", new UtcInstant(old), TOLERANCE, "V-201@1"),
                        ExclusionReason.infoInsufficient("V-301", new UtcInstant(older), TOLERANCE, "V-301@2"));
    }

    @Test
    void 情報不足の理由は情報が古くなった時刻と航海番号と許容時間と取得時刻を持つ() {
        Instant acquiredAt = JUDGED_AT.minus(Duration.ofHours(30));

        ExclusionReason reason = policy.evaluate(
                        List.of(leg("V-101", TOKYO, ROTTERDAM, "V-101@3", acquiredAt)), new UtcInstant(JUDGED_AT))
                .getFirst();

        assertThat(reason.violatedAt()).isEqualTo(new UtcInstant(acquiredAt.plus(TOLERANCE)));
        assertThat(reason.voyageNumber()).isEqualTo("V-101");
        assertThat(reason.freshnessTolerance()).isEqualTo(TOLERANCE);
        assertThat(reason.infoAcquiredAt()).contains(new UtcInstant(acquiredAt));
        assertThat(reason.infoVersion()).isEqualTo("V-101@3");
        assertThat(reason.portThreshold()).isEmpty();
    }

    @Test
    void 許容時間が違えば同じ取得時刻でも判定が変わる() {
        Instant acquiredAt = JUDGED_AT.minus(Duration.ofHours(25));
        List<Leg> legs = List.of(leg("V-101", TOKYO, ROTTERDAM, "V-101@3", acquiredAt));

        assertThat(policy.evaluate(legs, new UtcInstant(JUDGED_AT))).hasSize(1);
        assertThat(new InfoFreshnessPolicy(Duration.ofHours(48)).evaluate(legs, new UtcInstant(JUDGED_AT)))
                .isEmpty();
    }

    @ParameterizedTest(name = "許容時間 {0} は作れない")
    @ValueSource(strings = {"PT0S", "PT-1H"})
    void 許容時間が正でなければ作れない(String tolerance) {
        Duration duration = Duration.parse(tolerance);

        assertThatThrownBy(() -> new InfoFreshnessPolicy(duration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 許容時間がなければ作れない() {
        assertThatThrownBy(() -> new InfoFreshnessPolicy(null)).isInstanceOf(NullPointerException.class);
    }

    private static Leg leg(
            String voyageNumber, Location load, Location discharge, String infoVersion, Instant acquiredAt) {
        return new Leg(
                voyageNumber,
                load,
                discharge,
                new UtcInstant(Instant.parse("2026-10-10T00:00:00Z")),
                new UtcInstant(Instant.parse("2026-10-20T00:00:00Z")),
                infoVersion,
                new UtcInstant(acquiredAt));
    }
}
