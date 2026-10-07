package com.example.cargotracker.routing.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 経路候補探索（US-06 AC1）。R0.1 は直行と 1 回の積替え（区間 1〜2）を、確定的な順に列挙する（Bolt 17 の確認ポイント 8）。
 */
class RouteCandidateFinderTest {

    static final Location TOKYO = new Location("JPTYO");
    static final Location SINGAPORE = new Location("SGSIN");
    static final Location ROTTERDAM = new Location("NLRTM");
    static final Location HONG_KONG = new Location("HKHKG");
    static final UtcInstant JUDGED_AT = at("2026-10-07T03:00:00Z");
    static final RouteSpecification SPEC =
            new RouteSpecification(TOKYO, ROTTERDAM, at("2026-11-02T00:00:00Z"), "GENERAL");

    private final RouteCandidateFinder finder = new RouteCandidateFinder();

    @Test
    void 直行の航海から区間1つの候補を作り寄港の予定と情報版を写す() {
        Voyage direct = voyage(
                "V-101",
                call(TOKYO, null, "2026-10-08T00:00:00Z"),
                call(HONG_KONG, "2026-10-12T00:00:00Z", "2026-10-13T00:00:00Z"),
                call(ROTTERDAM, "2026-10-30T09:00:00Z", null));

        List<List<Leg>> candidates = finder.find(SPEC, List.of(direct), JUDGED_AT);

        assertThat(candidates)
                .containsExactly(List.of(new Leg(
                        "V-101",
                        TOKYO,
                        ROTTERDAM,
                        at("2026-10-08T00:00:00Z"),
                        at("2026-10-30T09:00:00Z"),
                        "V-101@1",
                        at("2026-10-01T06:10:00Z"))));
    }

    @Test
    void 積替えの港で到着より後に出発する航海とつないで区間2つの候補を作る() {
        Voyage feeder = voyage(
                "V-201", call(TOKYO, null, "2026-10-10T00:00:00Z"), call(SINGAPORE, "2026-10-20T00:00:00Z", null));
        Voyage mainline = voyage(
                "V-301", call(SINGAPORE, null, "2026-10-21T00:00:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        List<List<Leg>> candidates = finder.find(SPEC, List.of(mainline, feeder), JUDGED_AT);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst())
                .extracting(Leg::voyageNumber, Leg::load, Leg::discharge)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("V-201", TOKYO, SINGAPORE),
                        org.assertj.core.groups.Tuple.tuple("V-301", SINGAPORE, ROTTERDAM));
    }

    @Test
    void 積替えの港で到着より前に出発する航海とはつながない() {
        Voyage feeder = voyage(
                "V-201", call(TOKYO, null, "2026-10-10T00:00:00Z"), call(SINGAPORE, "2026-10-20T00:00:00Z", null));
        Voyage leftBefore = voyage(
                "V-302", call(SINGAPORE, null, "2026-10-19T23:59:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(feeder, leftBefore), JUDGED_AT)).isEmpty();
    }

    @Test
    void 到着と同時刻に出発する航海とはつなぐ() {
        Voyage feeder = voyage(
                "V-201", call(TOKYO, null, "2026-10-10T00:00:00Z"), call(SINGAPORE, "2026-10-20T00:00:00Z", null));
        Voyage sameTime = voyage(
                "V-303", call(SINGAPORE, null, "2026-10-20T00:00:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(feeder, sameTime), JUDGED_AT)).hasSize(1);
    }

    @Test
    void 判定時刻より前に出発する区間は列挙しない() {
        Voyage departed = voyage(
                "V-401", call(TOKYO, null, "2026-10-07T03:00:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));
        Voyage justAfter = voyage(
                "V-402", call(TOKYO, null, "2026-10-07T03:01:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(departed, justAfter), JUDGED_AT))
                .extracting(legs -> legs.getFirst().voyageNumber())
                .containsExactly("V-402");
    }

    @Test
    void 目的地から出発地へ向かう航海は列挙しない() {
        Voyage reverse = voyage(
                "V-501", call(ROTTERDAM, null, "2026-10-08T00:00:00Z"), call(TOKYO, "2026-10-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(reverse), JUDGED_AT)).isEmpty();
    }

    @Test
    void 同じ航海を2回使う列は列挙しない() {
        Voyage through = voyage(
                "V-601",
                call(TOKYO, null, "2026-10-08T00:00:00Z"),
                call(SINGAPORE, "2026-10-18T00:00:00Z", "2026-10-19T00:00:00Z"),
                call(ROTTERDAM, "2026-10-29T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(through), JUDGED_AT))
                .singleElement()
                .satisfies(legs -> assertThat(legs).hasSize(1));
    }

    @Test
    void 目的地を通り過ぎた先の港で積み替えて目的地へ戻る列は列挙しない() {
        // Bolt 17 レビュー D-60: 目的地に寄った後の港へ運んでから目的地へ戻す候補は、直行に劣り経路設計者を惑わせる
        Voyage passing = voyage(
                "V-701",
                call(TOKYO, null, "2026-10-08T00:00:00Z"),
                call(ROTTERDAM, "2026-10-28T00:00:00Z", "2026-10-28T12:00:00Z"),
                call(HONG_KONG, "2026-11-10T00:00:00Z", null));
        Voyage back = voyage(
                "V-702", call(HONG_KONG, null, "2026-11-11T00:00:00Z"), call(ROTTERDAM, "2026-11-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(passing, back), JUDGED_AT))
                .extracting(legs -> legs.stream().map(Leg::voyageNumber).toList())
                .containsExactly(List.of("V-701"));
    }

    @Test
    void 判定時刻の1分前に出発する区間は列挙しない() {
        Voyage before = voyage(
                "V-403", call(TOKYO, null, "2026-10-07T02:59:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(before), JUDGED_AT)).isEmpty();
    }

    @Test
    void 候補は到着予定の早い順に並び同時刻は航海番号の順() {
        Voyage late = voyage(
                "V-102", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(ROTTERDAM, "2026-10-31T00:00:00Z", null));
        Voyage earlyB = voyage(
                "V-104", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));
        Voyage earlyA = voyage(
                "V-103", call(TOKYO, null, "2026-10-09T00:00:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(late, earlyB, earlyA), JUDGED_AT))
                .extracting(legs -> legs.getFirst().voyageNumber())
                .containsExactly("V-103", "V-104", "V-102");
    }

    static Voyage voyage(String number, PortCall... calls) {
        return new Voyage(number, List.of(calls), number + "@1", at("2026-10-01T06:10:00Z"));
    }

    static PortCall call(Location port, String arrival, String departure) {
        return new PortCall(port, arrival == null ? null : at(arrival), departure == null ? null : at(departure));
    }

    static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}
