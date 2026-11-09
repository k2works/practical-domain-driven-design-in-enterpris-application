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
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 経路候補探索（US-06 AC1）。R0.1 は直行と 1 回の積替え（区間 1〜2）を、確定的な順に列挙する（Bolt 17 の確認ポイント 8）。
 * Bolt 37 で積替え 2 回（区間 3）まで広げた。列の中で同じ航海と同じ港を 2 回使わない（Bolt 37 の確認ポイント 2）。
 */
class RouteCandidateFinderTest {

    static final Location TOKYO = new Location("JPTYO");
    static final Location SINGAPORE = new Location("SGSIN");
    static final Location ROTTERDAM = new Location("NLRTM");
    static final Location HONG_KONG = new Location("HKHKG");
    static final Location COLOMBO = new Location("LKCMB");
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
                        at("2026-10-01T06:10:00Z"),
                        1,
                        3)));
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

    @Test
    void 積替え2回の区間3つの候補を作り到着予定の順に直行と並べる() {
        Voyage feeder = voyage(
                "V-201", call(TOKYO, null, "2026-10-10T00:00:00Z"), call(SINGAPORE, "2026-10-20T00:00:00Z", null));
        Voyage middle = voyage(
                "V-801", call(SINGAPORE, null, "2026-10-21T00:00:00Z"), call(COLOMBO, "2026-10-24T00:00:00Z", null));
        Voyage last = voyage(
                "V-802", call(COLOMBO, null, "2026-10-25T00:00:00Z"), call(ROTTERDAM, "2026-10-29T00:00:00Z", null));
        Voyage direct = voyage(
                "V-101", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(direct, last, middle, feeder), JUDGED_AT))
                .extracting(RouteCandidateFinderTest::voyageNumbers)
                .containsExactly(List.of("V-201", "V-801", "V-802"), List.of("V-101"));
    }

    @Test
    void 区間3つ目は前の区間の到着予定以後に出発するものだけをつなぐ() {
        Voyage feeder = voyage(
                "V-201", call(TOKYO, null, "2026-10-10T00:00:00Z"), call(SINGAPORE, "2026-10-20T00:00:00Z", null));
        Voyage middle = voyage(
                "V-801", call(SINGAPORE, null, "2026-10-21T00:00:00Z"), call(COLOMBO, "2026-10-24T00:00:00Z", null));
        Voyage leftBefore = voyage(
                "V-803", call(COLOMBO, null, "2026-10-23T23:59:00Z"), call(ROTTERDAM, "2026-10-29T00:00:00Z", null));
        Voyage sameTime = voyage(
                "V-804", call(COLOMBO, null, "2026-10-24T00:00:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(feeder, middle, leftBefore, sameTime), JUDGED_AT))
                .extracting(RouteCandidateFinderTest::voyageNumbers)
                .containsExactly(List.of("V-201", "V-801", "V-804"));
    }

    @Test
    void 区間4つでしか目的地に着かない列は列挙しない() {
        Voyage first = voyage(
                "V-901", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(HONG_KONG, "2026-10-10T00:00:00Z", null));
        Voyage second = voyage(
                "V-902", call(HONG_KONG, null, "2026-10-11T00:00:00Z"), call(SINGAPORE, "2026-10-14T00:00:00Z", null));
        Voyage third = voyage(
                "V-903", call(SINGAPORE, null, "2026-10-15T00:00:00Z"), call(COLOMBO, "2026-10-18T00:00:00Z", null));
        Voyage fourth = voyage(
                "V-904", call(COLOMBO, null, "2026-10-19T00:00:00Z"), call(ROTTERDAM, "2026-10-29T00:00:00Z", null));
        List<Voyage> voyages = List.of(first, second, third, fourth);
        // 航海がつながることを、出発地を香港にした 3 区間で先に確かめる（T-106）
        RouteSpecification fromHongKong =
                new RouteSpecification(HONG_KONG, ROTTERDAM, at("2026-11-02T00:00:00Z"), "GENERAL");
        assertThat(finder.find(fromHongKong, voyages, JUDGED_AT)).hasSize(1);

        assertThat(finder.find(SPEC, voyages, JUDGED_AT)).isEmpty();
    }

    @Test
    void 同じ航海を続けて2回使う列は列挙しない() {
        Voyage through = voyage(
                "V-601",
                call(TOKYO, null, "2026-10-08T00:00:00Z"),
                call(SINGAPORE, "2026-10-18T00:00:00Z", "2026-10-19T00:00:00Z"),
                call(COLOMBO, "2026-10-22T00:00:00Z", null));
        Voyage last = voyage(
                "V-802", call(COLOMBO, null, "2026-10-25T00:00:00Z"), call(ROTTERDAM, "2026-10-29T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(through, last), JUDGED_AT))
                .extracting(RouteCandidateFinderTest::voyageNumbers)
                .containsExactly(List.of("V-601", "V-802"));
    }

    @Test
    void 同じ航海を間を空けて2回使う列は列挙しない() {
        // V-611 の東京→シンガポール、V-612 のシンガポール→香港、V-611 の香港→ロッテルダムは時刻の上ではつながる
        Voyage mainline = voyage(
                "V-611",
                call(TOKYO, null, "2026-10-08T00:00:00Z"),
                call(SINGAPORE, "2026-10-10T00:00:00Z", "2026-10-11T00:00:00Z"),
                call(HONG_KONG, "2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z"),
                call(ROTTERDAM, "2026-10-30T00:00:00Z", null));
        Voyage shuttle = voyage(
                "V-612", call(SINGAPORE, null, "2026-10-12T00:00:00Z"), call(HONG_KONG, "2026-10-15T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(mainline, shuttle), JUDGED_AT))
                .extracting(RouteCandidateFinderTest::voyageNumbers)
                .containsExactly(List.of("V-611"));
    }

    @Test
    void 出発地に戻ってから出る列は列挙しない() {
        Voyage out = voyage(
                "V-621", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(SINGAPORE, "2026-10-10T00:00:00Z", null));
        Voyage back = voyage(
                "V-622", call(SINGAPORE, null, "2026-10-11T00:00:00Z"), call(TOKYO, "2026-10-13T00:00:00Z", null));
        Voyage direct = voyage(
                "V-623", call(TOKYO, null, "2026-10-14T00:00:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(out, back, direct), JUDGED_AT))
                .extracting(RouteCandidateFinderTest::voyageNumbers)
                .containsExactly(List.of("V-623"));
    }

    @Test
    void 途中の区間も目的地を通り過ぎた先の港へは運ばない() {
        Voyage feeder = voyage(
                "V-201", call(TOKYO, null, "2026-10-10T00:00:00Z"), call(SINGAPORE, "2026-10-20T00:00:00Z", null));
        Voyage passing = voyage(
                "V-631",
                call(SINGAPORE, null, "2026-10-21T00:00:00Z"),
                call(ROTTERDAM, "2026-10-28T00:00:00Z", "2026-10-28T12:00:00Z"),
                call(HONG_KONG, "2026-11-10T00:00:00Z", null));
        Voyage back = voyage(
                "V-702", call(HONG_KONG, null, "2026-11-11T00:00:00Z"), call(ROTTERDAM, "2026-11-30T00:00:00Z", null));

        assertThat(finder.find(SPEC, List.of(feeder, passing, back), JUDGED_AT))
                .extracting(RouteCandidateFinderTest::voyageNumbers)
                .containsExactly(List.of("V-201", "V-631"));
    }

    /** 確定した区間（引き直しの元。Bolt 37d2）。 */
    private static final Leg CONFIRMED_LEG = new Leg(
            "V-101",
            TOKYO,
            ROTTERDAM,
            at("2026-10-08T00:00:00Z"),
            at("2026-10-30T09:00:00Z"),
            "V-101@1",
            at("2026-10-01T06:10:00Z"));

    @Test
    void 区間を今の航海で引き直すと情報版と時刻と取得時刻が今のものになる() {
        Voyage updated = new Voyage(
                "V-101",
                List.of(
                        call(TOKYO, null, "2026-10-08T06:00:00Z"),
                        call(HONG_KONG, "2026-10-12T00:00:00Z", "2026-10-13T00:00:00Z"),
                        call(ROTTERDAM, "2026-11-03T00:00:00Z", null)),
                "V-101@2",
                at("2026-10-08T05:00:00Z"));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(updated)))
                .contains(new Leg(
                        "V-101",
                        TOKYO,
                        ROTTERDAM,
                        at("2026-10-08T06:00:00Z"),
                        at("2026-11-03T00:00:00Z"),
                        "V-101@2",
                        at("2026-10-08T05:00:00Z"),
                        1,
                        3));
    }

    @Test
    void 同じ航海番号の航海がなければ引き直せない() {
        Voyage other = voyage(
                "V-102", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(ROTTERDAM, "2026-10-30T09:00:00Z", null));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(other))).isEmpty();
    }

    @Test
    void 積地に寄らなくなれば引き直せない() {
        Voyage updated = voyage(
                "V-101", call(HONG_KONG, null, "2026-10-13T00:00:00Z"), call(ROTTERDAM, "2026-10-30T09:00:00Z", null));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(updated))).isEmpty();
    }

    @Test
    void 揚地に寄らなくなれば引き直せない() {
        Voyage updated = voyage(
                "V-101", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(HONG_KONG, "2026-10-12T00:00:00Z", null));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(updated))).isEmpty();
    }

    @Test
    void 寄る順が逆になれば引き直せない() {
        Voyage updated = voyage(
                "V-101", call(ROTTERDAM, null, "2026-10-08T00:00:00Z"), call(TOKYO, "2026-10-30T09:00:00Z", null));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(updated))).isEmpty();
    }

    @Test
    void 積地の出発予定か揚地の到着予定がなければ引き直せない() {
        Voyage noDeparture = voyage(
                "V-101", call(TOKYO, "2026-10-07T00:00:00Z", null), call(ROTTERDAM, "2026-10-30T09:00:00Z", null));
        Voyage noArrival = voyage(
                "V-101", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(ROTTERDAM, null, "2026-10-30T09:00:00Z"));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(noDeparture))).isEmpty();
        assertThat(finder.refresh(CONFIRMED_LEG, List.of(noArrival))).isEmpty();
    }

    @Test
    void 到着が出発より後でなければ引き直せない() {
        Voyage updated = voyage(
                "V-101", call(TOKYO, null, "2026-10-30T09:00:00Z"), call(ROTTERDAM, "2026-10-30T09:00:00Z", null));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(updated))).isEmpty();
    }

    @Test
    void 行8_位置のない区間は同じ港に2度寄って積地と揚地の組が2つあれば予定が一致する組があっても選ばずに引き直せない() {
        Voyage updated = voyage(
                "V-101",
                call(TOKYO, null, "2026-10-08T00:00:00Z"),
                call(ROTTERDAM, "2026-10-30T09:00:00Z", "2026-10-31T00:00:00Z"),
                call(TOKYO, "2026-11-20T00:00:00Z", "2026-11-21T00:00:00Z"),
                call(ROTTERDAM, "2026-12-10T00:00:00Z", null));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(updated))).isEmpty();
    }

    @Test
    void 揚地が積地より前にも寄るときは積地の後で最初に揚地に寄る寄港で引き直す() {
        Voyage updated = voyage(
                "V-101",
                call(ROTTERDAM, null, "2026-10-01T00:00:00Z"),
                call(TOKYO, "2026-10-07T00:00:00Z", "2026-10-08T00:00:00Z"),
                call(ROTTERDAM, "2026-10-30T09:00:00Z", null));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(updated))).contains(confirmedLegAt(2, 3));
    }

    @Test
    void 行8_位置のない区間は積地の後で揚地に2度寄れば確定した寄港が分からないので選ばずに引き直せない() {
        // Bolt 37d2 レビュー M-1。候補探索は積地から後のどの寄港も揚地にできるので、A→B→C→B では A→B が 2 つある
        Voyage updated = voyage(
                "V-101",
                call(TOKYO, null, "2026-10-08T00:00:00Z"),
                call(ROTTERDAM, "2026-10-30T09:00:00Z", "2026-10-31T00:00:00Z"),
                call(HONG_KONG, "2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z"),
                call(ROTTERDAM, "2026-11-30T00:00:00Z", null));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(updated))).isEmpty();
    }

    @Test
    void 積地に2度寄っても出発予定のある寄港が1つなら引き直せる() {
        Voyage updated = voyage(
                "V-101",
                call(TOKYO, "2026-10-01T00:00:00Z", null),
                call(HONG_KONG, "2026-10-04T00:00:00Z", "2026-10-05T00:00:00Z"),
                call(TOKYO, null, "2026-10-08T00:00:00Z"),
                call(ROTTERDAM, "2026-10-30T09:00:00Z", null));

        assertThat(finder.refresh(CONFIRMED_LEG, List.of(updated))).contains(confirmedLegAt(3, 4));
    }

    /** {@link #CONFIRMED_LEG} を今の航海の寄港の位置で引き直した区間（Bolt 39f）。 */
    private static Leg confirmedLegAt(int loadCallNo, int dischargeCallNo) {
        return new Leg(
                CONFIRMED_LEG.voyageNumber(),
                CONFIRMED_LEG.load(),
                CONFIRMED_LEG.discharge(),
                CONFIRMED_LEG.departureAt(),
                CONFIRMED_LEG.arrivalAt(),
                CONFIRMED_LEG.infoVersion(),
                CONFIRMED_LEG.infoAcquiredAt(),
                loadCallNo,
                dischargeCallNo);
    }

    @Nested
    class Bolt39e_寄港の時刻の順が矛盾した航海 {

        private static final Voyage GOOD = voyage(
                "V-GOOD", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(ROTTERDAM, "2026-10-30T00:00:00Z", null));

        static Stream<Arguments> brokenSecondCalls() {
            return Stream.of(
                    Arguments.of("到着が前の寄港の出発より前", "2026-10-07T23:00:00Z"),
                    Arguments.of("到着が前の寄港の出発と同時刻", "2026-10-08T00:00:00Z"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("brokenSecondCalls")
        void 行44_矛盾した組だけ区間を作らずその航海のほかの組とほかの航海の候補は残る(String description, String brokenArrival) {
            Voyage broken = voyage(
                    "V-BROKEN",
                    call(TOKYO, null, "2026-10-08T00:00:00Z"),
                    call(SINGAPORE, brokenArrival, "2026-10-09T00:00:00Z"),
                    call(ROTTERDAM, "2026-11-01T00:00:00Z", null));

            List<List<Leg>> found = finder.find(SPEC, List.of(broken, GOOD), JUDGED_AT);

            assertThat(found)
                    .as(description)
                    .extracting(RouteCandidateFinderTest::voyageNumbers)
                    .containsExactly(List.of("V-GOOD"), List.of("V-BROKEN"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("brokenSecondCalls")
        void 行44_目的地への組が矛盾した航海はその航海の候補がなくほかの航海の候補は残る(String description, String brokenArrival) {
            Voyage broken =
                    voyage("V-BROKEN", call(TOKYO, null, "2026-10-08T00:00:00Z"), call(ROTTERDAM, brokenArrival, null));

            assertThat(finder.find(SPEC, List.of(broken, GOOD), JUDGED_AT))
                    .as(description)
                    .extracting(RouteCandidateFinderTest::voyageNumbers)
                    .containsExactly(List.of("V-GOOD"));
        }
    }

    /** 確定の区間の寄港の位置（【前の決定の変更】(5)。Bolt 39f の決定 (3)）。 */
    @Nested
    class Bolt39f_寄港の位置 {

        private static Leg confirmedLeg(Integer loadCallNo, Integer dischargeCallNo) {
            return new Leg(
                    "V-101",
                    TOKYO,
                    ROTTERDAM,
                    at("2026-10-08T00:00:00Z"),
                    at("2026-10-30T09:00:00Z"),
                    "V-101@1",
                    at("2026-10-01T06:10:00Z"),
                    loadCallNo,
                    dischargeCallNo);
        }

        /** 東京 → ロッテルダム → 香港 → ロッテルダム（組は (1,2) と (1,4)）。 */
        private static Voyage rotterdamTwice(String firstArrival, String secondArrival) {
            return new Voyage(
                    "V-101",
                    List.of(
                            call(TOKYO, null, "2026-10-08T00:00:00Z"),
                            call(ROTTERDAM, firstArrival, "2026-10-31T00:00:00Z"),
                            call(HONG_KONG, "2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z"),
                            call(ROTTERDAM, secondArrival, null)),
                    "V-101@2",
                    at("2026-10-08T05:00:00Z"));
        }

        static Stream<Arguments> tokyoTwiceVoyages() {
            return Stream.of(
                    Arguments.of(
                            "運航の順に書いた航海",
                            voyage(
                                    "V-AGAIN",
                                    call(TOKYO, null, "2026-10-08T00:00:00Z"),
                                    call(SINGAPORE, "2026-10-12T00:00:00Z", "2026-10-13T00:00:00Z"),
                                    call(TOKYO, "2026-10-17T00:00:00Z", "2026-10-18T00:00:00Z"),
                                    call(ROTTERDAM, "2026-10-30T00:00:00Z", null)),
                            "2026-10-08T00:00:00Z",
                            "2026-10-18T00:00:00Z"),
                    Arguments.of(
                            "前の寄港の出発を後の寄港より遅く書いた航海（位置は書いた順。T-245）",
                            voyage(
                                    "V-AGAIN",
                                    call(TOKYO, null, "2026-10-18T00:00:00Z"),
                                    call(SINGAPORE, "2026-10-12T00:00:00Z", "2026-10-13T00:00:00Z"),
                                    call(TOKYO, "2026-10-07T12:00:00Z", "2026-10-08T00:00:00Z"),
                                    call(ROTTERDAM, "2026-10-30T00:00:00Z", null)),
                            "2026-10-18T00:00:00Z",
                            "2026-10-08T00:00:00Z"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tokyoTwiceVoyages")
        void 行4_候補探索は区間ごとに運航の順の1始まりの位置を持つ(
                String description, Voyage voyage, String firstCallDeparture, String thirdCallDeparture) {
            List<List<Leg>> found = finder.find(SPEC, List.of(voyage), JUDGED_AT);

            assertThat(found)
                    .as(description)
                    .extracting(
                            legs -> legs.getFirst().departureAt(),
                            legs -> legs.getFirst().loadCallNo(),
                            legs -> legs.getFirst().dischargeCallNo())
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(at(firstCallDeparture), 1, 4),
                            org.assertj.core.groups.Tuple.tuple(at(thirdCallDeparture), 3, 4));
        }

        @Test
        void 行5_組が1つなら位置がずれていても今のまま引き直す() {
            Voyage updated = new Voyage(
                    "V-101",
                    List.of(
                            call(HONG_KONG, null, "2026-10-01T00:00:00Z"),
                            call(TOKYO, "2026-10-07T00:00:00Z", "2026-10-08T06:00:00Z"),
                            call(ROTTERDAM, "2026-11-01T00:00:00Z", null)),
                    "V-101@2",
                    at("2026-10-08T05:00:00Z"));

            assertThat(finder.refresh(confirmedLeg(1, 2), List.of(updated))).hasValueSatisfying(leg -> {
                assertThat(leg.departureAt()).isEqualTo(at("2026-10-08T06:00:00Z"));
                assertThat(leg.arrivalAt()).isEqualTo(at("2026-11-01T00:00:00Z"));
                assertThat(leg.loadCallNo()).isEqualTo(2);
                assertThat(leg.dischargeCallNo()).isEqualTo(3);
            });
        }

        @ParameterizedTest(name = "位置 ({0},{1}) は到着 {2}")
        @CsvSource({"1, 2, 2026-10-30T12:00:00Z", "1, 4, 2026-12-01T00:00:00Z"})
        void 行6_組が2つ以上で予定の一致する組がなければ位置の組で新しい時刻の区間を作る(int loadCallNo, int dischargeCallNo, String expectedArrival) {
            Voyage updated = rotterdamTwice("2026-10-30T12:00:00Z", "2026-12-01T00:00:00Z");

            assertThat(finder.refresh(confirmedLeg(loadCallNo, dischargeCallNo), List.of(updated)))
                    .hasValueSatisfying(leg -> {
                        assertThat(leg.arrivalAt()).isEqualTo(at(expectedArrival));
                        assertThat(leg.dischargeCallNo()).isEqualTo(dischargeCallNo);
                        assertThat(leg.infoVersion()).isEqualTo("V-101@2");
                    });
        }

        @Test
        void 行6b_組が2つ以上で出発と到着の一致する組が1つならその組を選び位置は見ない() {
            // 確定の後に先頭へ寄港が 1 つ増えて位置がずれた。予定が同じ組は (2,3) で、位置 (1,4) の寄港は別の組を指す
            Voyage updated = new Voyage(
                    "V-101",
                    List.of(
                            call(HONG_KONG, null, "2026-10-01T00:00:00Z"),
                            call(TOKYO, "2026-10-07T00:00:00Z", "2026-10-08T00:00:00Z"),
                            call(ROTTERDAM, "2026-10-30T09:00:00Z", "2026-10-31T00:00:00Z"),
                            call(HONG_KONG, "2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z"),
                            call(ROTTERDAM, "2026-12-01T00:00:00Z", null)),
                    "V-101@2",
                    at("2026-10-08T05:00:00Z"));

            assertThat(finder.refresh(confirmedLeg(1, 4), List.of(updated))).hasValueSatisfying(leg -> {
                assertThat(leg.arrivalAt()).isEqualTo(at("2026-10-30T09:00:00Z"));
                assertThat(leg.loadCallNo()).isEqualTo(2);
                assertThat(leg.dischargeCallNo()).isEqualTo(3);
            });
        }

        /** 東京に同じ出発の時刻で 2 度寄る。区間の予定に一致する組は (1,4) と (3,4) の 2 つ。 */
        private static Voyage tokyoTwiceAtSameDeparture() {
            return new Voyage(
                    "V-101",
                    List.of(
                            call(TOKYO, null, "2026-10-08T00:00:00Z"),
                            call(HONG_KONG, "2026-10-02T00:00:00Z", "2026-10-03T00:00:00Z"),
                            call(TOKYO, "2026-10-07T00:00:00Z", "2026-10-08T00:00:00Z"),
                            call(ROTTERDAM, "2026-10-30T09:00:00Z", null)),
                    "V-101@2",
                    at("2026-10-08T05:00:00Z"));
        }

        @ParameterizedTest(name = "位置 ({0},{1})")
        @CsvSource({"1, 4", "3, 4"})
        void 行6b_出発と到着の一致する組が2つ以上なら位置の組を選ぶ(int loadCallNo, int dischargeCallNo) {
            assertThat(finder.refresh(confirmedLeg(loadCallNo, dischargeCallNo), List.of(tokyoTwiceAtSameDeparture())))
                    .hasValueSatisfying(leg -> {
                        assertThat(leg.loadCallNo()).isEqualTo(loadCallNo);
                        assertThat(leg.dischargeCallNo()).isEqualTo(dischargeCallNo);
                    });
        }

        @Test
        void 行7_出発と到着の一致する組が2つ以上で位置の組がなければ選ばずに引き直せない() {
            assertThat(finder.refresh(confirmedLeg(2, 4), List.of(tokyoTwiceAtSameDeparture())))
                    .isEmpty();
        }

        static Stream<Arguments> unusablePositions() {
            Voyage updated = rotterdamTwice("2026-10-30T12:00:00Z", "2026-12-01T00:00:00Z");
            Voyage middleWithoutArrival = new Voyage(
                    "V-101",
                    List.of(
                            call(TOKYO, null, "2026-10-08T00:00:00Z"),
                            call(ROTTERDAM, null, "2026-10-31T00:00:00Z"),
                            call(HONG_KONG, "2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z"),
                            call(ROTTERDAM, "2026-12-01T00:00:00Z", null),
                            call(HONG_KONG, "2026-12-05T00:00:00Z", "2026-12-06T00:00:00Z"),
                            call(ROTTERDAM, "2026-12-10T00:00:00Z", null)),
                    "V-101@2",
                    at("2026-10-08T05:00:00Z"));
            Voyage loadWithoutDeparture = new Voyage(
                    "V-101",
                    List.of(
                            call(TOKYO, null, "2026-10-08T00:00:00Z"),
                            call(TOKYO, "2026-10-09T00:00:00Z", null),
                            call(ROTTERDAM, "2026-10-30T12:00:00Z", "2026-10-31T00:00:00Z"),
                            call(HONG_KONG, "2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z"),
                            call(ROTTERDAM, "2026-12-01T00:00:00Z", null)),
                    "V-101@2",
                    at("2026-10-08T05:00:00Z"));
            Voyage arrivalNotAfterDeparture = rotterdamTwice("2026-10-07T00:00:00Z", "2026-12-01T00:00:00Z");
            return Stream.of(
                    Arguments.of("揚地の位置の港が違う", updated, confirmedLeg(1, 3)),
                    Arguments.of("位置の組が組の中にない（積地の位置の港が違う）", updated, confirmedLeg(2, 4)),
                    Arguments.of("揚地の位置が寄港の数を超える", updated, confirmedLeg(1, 9)),
                    Arguments.of("積地の位置が寄港の数を超える", updated, confirmedLeg(9, 10)),
                    Arguments.of("揚地の位置の寄港に到着がない", middleWithoutArrival, confirmedLeg(1, 2)),
                    Arguments.of("積地の位置の寄港に出発がない", loadWithoutDeparture, confirmedLeg(2, 3)),
                    Arguments.of("位置の組の到着が出発より後でない", arrivalNotAfterDeparture, confirmedLeg(1, 2)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unusablePositions")
        void 行7_一致する組がなく位置の組を使えなければ選ばずに引き直せない(String description, Voyage voyage, Leg leg) {
            assertThat(finder.refresh(leg, List.of(voyage))).as(description).isEmpty();
        }
    }

    static List<String> voyageNumbers(List<Leg> legs) {
        return legs.stream().map(Leg::voyageNumber).toList();
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
