package com.example.cargotracker.routing.domain.model.rules;

import static com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluatorTest.leg;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteCargoCategory;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 経路設計できる貨物（BR-02、BR-03、R-INV-02。Bolt 39i の行 1〜4・7）。 */
class RoutableCargoPolicyTest {

    static final Location TOKYO = new Location("JPTYO");
    static final Location SINGAPORE = new Location("SGSIN");
    static final Location HONG_KONG = new Location("HKHKG");
    static final Location ROTTERDAM = new Location("NLRTM");

    /** 3 区間。区間ごとに出発予定と情報版が違う。 */
    private static List<Leg> threeLegs() {
        return List.of(
                leg("V-A", TOKYO, SINGAPORE, "2026-10-08T00:00:00Z", "2026-10-09T00:00:00Z", "V-A@3"),
                leg("V-B", SINGAPORE, HONG_KONG, "2026-10-09T10:00:00Z", "2026-10-12T00:00:00Z", "V-B@5"),
                leg("V-C", HONG_KONG, ROTTERDAM, "2026-10-12T10:00:00Z", "2026-10-29T00:00:00Z", "V-C@7"));
    }

    @Test
    void 一般貨物は理由が空() {
        assertThat(RoutableCargoPolicy.evaluate(RouteCargoCategory.GENERAL, threeLegs()))
                .isEmpty();
    }

    @ParameterizedTest(name = "{0} は貨物種別の理由が一つ")
    @EnumSource(
            value = RouteCargoCategory.class,
            names = {"DANGEROUS", "REEFER", "OTHER_SPECIAL"})
    void 特殊貨物は貨物種別に対応しない理由が一つで閾値はその種別(RouteCargoCategory category) {
        List<ExclusionReason> reasons = RoutableCargoPolicy.evaluate(category, threeLegs());

        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst().code()).isEqualTo(ExclusionReasonCode.CARGO_NOT_SUPPORTED);
        assertThat(reasons.getFirst().threshold()).isEqualTo(new ExclusionThreshold.UnsupportedCargo(category));
    }

    @Test
    void 知らない値は一般貨物として通さず除外する() {
        List<ExclusionReason> reasons = RoutableCargoPolicy.evaluate(RouteCargoCategory.UNRECOGNIZED, threeLegs());

        assertThat(reasons)
                .extracting(ExclusionReason::threshold)
                .containsExactly(new ExclusionThreshold.UnsupportedCargo(RouteCargoCategory.UNRECOGNIZED));
    }

    @Test
    void 不適合の時刻は最初の区間の出発予定で参照情報版は最初の区間の採用情報版() {
        ExclusionReason reason = RoutableCargoPolicy.evaluate(RouteCargoCategory.DANGEROUS, threeLegs())
                .getFirst();

        assertThat(reason.violatedAt()).isEqualTo(new UtcInstant(Instant.parse("2026-10-08T00:00:00Z")));
        assertThat(reason.infoVersion()).isEqualTo("V-A@3");
    }

    @ParameterizedTest(name = "区間のない候補は {0} でも例外にせず理由を作らない")
    @EnumSource(RouteCargoCategory.class)
    void 区間のない候補には理由を作らない(RouteCargoCategory category) {
        assertThat(RoutableCargoPolicy.evaluate(category, List.of())).isEmpty();
    }
}
