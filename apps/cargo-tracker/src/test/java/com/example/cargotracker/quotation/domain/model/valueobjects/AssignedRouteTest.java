package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 割り当てた経路と区間の写し（Bolt 20）。 */
class AssignedRouteTest {

    private final AssignedRouteLeg first =
            leg("V-381", "JPTYO", "SGSIN", "2099-10-12T03:00:00Z", "2099-10-20T08:00:00Z");
    private final AssignedRouteLeg second =
            leg("V-417", "SGSIN", "NLRTM", "2099-10-22T06:00:00Z", "2099-10-30T09:00:00Z");

    @Test
    void 到着予定は最後の区間の到着予定() {
        AssignedRoute route = new AssignedRoute("RC-2026-0001", 1, at("2026-10-08T02:00:00Z"), List.of(first, second));

        assertThat(route.arrivalAt()).isEqualTo(at("2099-10-30T09:00:00Z"));
    }

    @Test
    void 案件番号と経路版番号が同じなら同じ経路版() {
        AssignedRoute route = new AssignedRoute("RC-2026-0001", 1, at("2026-10-08T02:00:00Z"), List.of(first));

        assertThat(route.isSameVersionAs(
                        new AssignedRoute("RC-2026-0001", 1, at("2026-10-08T03:00:00Z"), List.of(second))))
                .isTrue();
        assertThat(route.isSameVersionAs(
                        new AssignedRoute("RC-2026-0001", 2, at("2026-10-08T02:00:00Z"), List.of(first))))
                .isFalse();
        assertThat(route.isSameVersionAs(
                        new AssignedRoute("RC-2026-0002", 1, at("2026-10-08T02:00:00Z"), List.of(first))))
                .isFalse();
    }

    @Test
    void 区間のない経路と経路版番号0と到着が出発より後でない区間は作れない() {
        UtcInstant confirmedAt = at("2026-10-08T02:00:00Z");

        assertThatThrownBy(() -> new AssignedRoute("RC-2026-0001", 1, confirmedAt, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssignedRoute("RC-2026-0001", 0, confirmedAt, List.of(first)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leg("V-381", "JPTYO", "SGSIN", "2099-10-12T03:00:00Z", "2099-10-12T03:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AssignedRouteLeg leg(
            String voyage, String load, String discharge, String departure, String arrival) {
        return new AssignedRouteLeg(voyage, new Location(load), new Location(discharge), at(departure), at(arrival));
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
