package com.example.cargotracker.routing.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 案件一覧（S-05）の見積りの期限切れの境界（Bolt 19 レビュー）。期限と同時刻は期限切れ（見積りの失効 Q-INV-07 と同じ向き）。 */
class RoutingCaseViewsTest {

    static final UtcInstant EXPIRES_AT = new UtcInstant(Instant.parse("2026-10-08T09:00:00Z"));

    @ParameterizedTest(name = "いまが {0} なら期限切れは {1}")
    @CsvSource({"2026-10-08T08:59:00Z, false", "2026-10-08T09:00:00Z, true", "2026-10-08T09:01:00Z, true"})
    void 期限と同時刻と後は期限切れ(String now, boolean expired) {
        RoutingCaseSummary summary = new RoutingCaseSummary(
                new RoutingCaseNumber(2026, 1),
                "TR-2026-0001",
                1,
                new Location("JPTYO"),
                new Location("NLRTM"),
                new UtcInstant(Instant.parse("2026-11-02T00:00:00Z")),
                new UtcInstant(Instant.parse("2026-10-06T02:00:00Z")),
                RouteVersionStatus.CANDIDATES_PRESENTED,
                EXPIRES_AT);

        assertThat(RoutingCaseViews.row(summary, Instant.parse(now)).quotationExpired())
                .isEqualTo(expired);
    }

    @Test
    void 区間3つの後の接続の理由はその接続の時間を示す() {
        // Bolt 37: 接続の時間は、理由の時刻に出発する区間とその前の区間から引く（2 つ目の接続）
        List<Leg> legs = List.of(
                leg("V-201", "JPTYO", "SGSIN", "2026-10-10T00:00:00Z", "2026-10-20T00:00:00Z"),
                leg("V-401", "SGSIN", "LKCMB", "2026-10-20T10:00:00Z", "2026-10-23T00:00:00Z"),
                leg("V-501", "LKCMB", "NLRTM", "2026-10-24T00:00:00Z", "2026-11-01T00:00:00Z"));
        ExclusionReason reason = ExclusionReason.notConnectable(
                new UtcInstant(Instant.parse("2026-10-24T00:00:00Z")), new Location("LKCMB"), "V-501@1");

        assertThat(RoutingCaseViews.reason(reason, legs)).startsWith("接続を判定できない（LKCMB に次の出発の時刻で有効な接続時間規則がない。接続 1 日）");
    }

    static Leg leg(String voyage, String load, String discharge, String departure, String arrival) {
        return new Leg(
                voyage,
                new Location(load),
                new Location(discharge),
                new UtcInstant(Instant.parse(departure)),
                new UtcInstant(Instant.parse(arrival)),
                voyage + "@1",
                new UtcInstant(Instant.parse("2026-10-01T06:10:00Z")));
    }
}
