package com.example.cargotracker.routing.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
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
}
