package com.example.cargotracker.tracking.domain.model.valueobjects;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.leg;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.twoLegs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 予定と予定区間（T-INV-10・T-INV-12。Bolt 25）。 */
class ScheduleTest {

    @Test
    void 区間の到着予定は出発予定より後でなければならない() {
        assertThatThrownBy(() -> leg("V100", "JPTYO", "KRPUS", "2026-11-03T00:00:00Z", "2026-11-03T00:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("V100");
    }

    @Test
    void 当初の到着予定は最後の区間の到着予定() {
        Schedule schedule = new Schedule("RC-2026-0001", 1, twoLegs());

        assertThat(schedule.finalArrival()).isEqualTo(at("2026-11-15T00:00:00Z"));
        assertThat(schedule.legs()).hasSize(2);
    }

    @Test
    void 区間がない予定は作れない() {
        assertThatThrownBy(() -> new Schedule("RC-2026-0001", 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("区間");
    }

    @Test
    void 前の区間の揚地と次の区間の積地がつながらない予定は作れない() {
        List<ScheduledLeg> legs = List.of(
                leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z"),
                leg("V200", "CNSHA", "USLAX", "2026-11-04T00:00:00Z", "2026-11-15T00:00:00Z"));

        assertThatThrownBy(() -> new Schedule("RC-2026-0001", 1, legs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("つながりません");
    }

    @Test
    void 次の区間が前の区間の到着より前に出発する予定は作れない() {
        List<ScheduledLeg> legs = List.of(
                leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z"),
                leg("V200", "KRPUS", "USLAX", "2026-11-02T00:00:00Z", "2026-11-15T00:00:00Z"));

        assertThatThrownBy(() -> new Schedule("RC-2026-0001", 1, legs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("V200");
    }

    @Test
    void 経路版番号は1から() {
        assertThatThrownBy(() -> new Schedule("RC-2026-0001", 0, twoLegs()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 渡した区間の列を後から変えても予定は変わらない() {
        List<ScheduledLeg> legs = new ArrayList<>(twoLegs());
        Schedule schedule = new Schedule("RC-2026-0001", 1, legs);

        legs.clear();

        assertThat(schedule.legs()).hasSize(2);
    }
}
