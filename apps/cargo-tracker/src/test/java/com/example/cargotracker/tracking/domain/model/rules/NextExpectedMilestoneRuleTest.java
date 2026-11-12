package com.example.cargotracker.tracking.domain.model.rules;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.leg;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.SchedulePoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 次の予定の実績の規則（FRS-02。Bolt 39g4 の状態の組み合わせの行 3〜5。【業務の判断】(aq)）。予定は 2 区間（区間 1: JPTYO から KRPUS、
 * 出発予定 D1 2026-11-01 00:00、到着予定 A1 2026-11-03 00:00。区間 2: KRPUS から USLAX、出発予定 D2 2026-11-04 00:00、到着予定 A2
 * 2026-11-15 00:00。UTC）。次の予定の実績は「種類 場所 予定時刻」の表記で比べる（なければ「なし」）。
 */
class NextExpectedMilestoneRuleTest {

    private static final String D1 = "DEPARTURE JPTYO 2026-11-01T00:00:00Z";
    private static final String A1 = "TRANSSHIPMENT KRPUS 2026-11-03T00:00:00Z";
    private static final String D2 = "DEPARTURE KRPUS 2026-11-04T00:00:00Z";
    private static final String A2 = "ARRIVAL USLAX 2026-11-15T00:00:00Z";
    private static final String NONE = "なし";
    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-000000039401"));

    private final NextExpectedMilestoneRule rule = new NextExpectedMilestoneRule();

    /**
     * 行 3 (a)〜(h)。採用の実績の列（「種類 場所 発生時刻」を「;」でつなぐ。列の順が実績番号の順）と期待の組（T-244）。発生時刻は予定時刻の
     * 1 時間後にそろえ、(h) だけ区間 1 の出発の発生を積替の後にする（登録の順・発生時刻の順でなく予定の順で決まることを縛る）。
     */
    @ParameterizedTest(name = "行3 {0}")
    @MethodSource("nextRows")
    void Bolt39g4_行3_採用の実績を結んだ最も後の予定の時点の次(String row, String adopted, String expected) {
        assertThat(describe(rule.next(schedule(), adoptedMilestones(adopted)))).isEqualTo(expected);
    }

    static Stream<Arguments> nextRows() {
        return Stream.of(
                Arguments.of("(a) 実績なし", "", D1),
                Arguments.of("(b) 区間 1 の出発", "DEPARTURE JPTYO 2026-11-01T01:00:00Z", A1),
                Arguments.of(
                        "(c) 区間 1 の出発と積替",
                        "DEPARTURE JPTYO 2026-11-01T01:00:00Z;TRANSSHIPMENT KRPUS 2026-11-03T01:00:00Z",
                        D2),
                Arguments.of(
                        "(d) (c) に区間 2 の出発",
                        "DEPARTURE JPTYO 2026-11-01T01:00:00Z;TRANSSHIPMENT KRPUS 2026-11-03T01:00:00Z;"
                                + "DEPARTURE KRPUS 2026-11-04T01:00:00Z",
                        A2),
                Arguments.of(
                        "(e) (d) に到着",
                        "DEPARTURE JPTYO 2026-11-01T01:00:00Z;TRANSSHIPMENT KRPUS 2026-11-03T01:00:00Z;"
                                + "DEPARTURE KRPUS 2026-11-04T01:00:00Z;ARRIVAL USLAX 2026-11-15T01:00:00Z",
                        NONE),
                Arguments.of(
                        "(f) 集荷と搬入だけ",
                        "PICKUP JPTYO 2026-10-31T01:00:00Z;RECEIPT_AT_ORIGIN JPTYO 2026-10-31T05:00:00Z",
                        D1),
                Arguments.of("(g) 区間 1 の実績が抜けて区間 2 の出発だけ", "DEPARTURE KRPUS 2026-11-04T01:00:00Z", A2),
                Arguments.of(
                        "(h) 区間 1 の出発（発生が遅い）と積替（登録が先）",
                        "TRANSSHIPMENT KRPUS 2026-11-03T01:00:00Z;DEPARTURE JPTYO 2026-11-03T05:00:00Z",
                        D2));
    }

    /** 行 3 (i)。積替予定 A1 と区間 2 の出発予定 D2 が同じ時刻の予定で、積替だけを採用すると次は区間 2 の出発（時点の列の位置で次を決める）。 */
    @Test
    void Bolt39g4_行3i_同じ時刻の積替の次は次の区間の出発() {
        Schedule sameTime = new Schedule(
                "RC-2026-0001",
                1,
                List.of(
                        leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z"),
                        leg("V200", "KRPUS", "USLAX", "2026-11-03T00:00:00Z", "2026-11-15T00:00:00Z")));

        assertThat(describe(rule.next(sameTime, adoptedMilestones("TRANSSHIPMENT KRPUS 2026-11-03T01:00:00Z"))))
                .isEqualTo("DEPARTURE KRPUS 2026-11-03T00:00:00Z");
    }

    /** 行 4。区間 1 の出発が確認中・下書き・保持のみなら次の予定の実績は区間 1 の出発のまま（採用だけを使う。T-INV-08 と同じ）。 */
    @ParameterizedTest(name = "行4 {0}")
    @EnumSource(
            value = MilestoneState.class,
            names = {"UNDER_REVIEW", "DRAFT", "RETAINED_ONLY"})
    void Bolt39g4_行4_採用でない実績は次の予定の実績を進めない(MilestoneState state) {
        Milestone milestone = new Milestone(
                1,
                MilestoneKind.DEPARTURE,
                new Location("JPTYO"),
                at("2026-11-01T01:00:00Z"),
                source(1),
                state,
                REGISTRANT,
                at("2026-11-02T00:00:00Z"));

        assertThat(describe(rule.next(schedule(), List.of(milestone)))).isEqualTo(D1);
    }

    /** 行 5。区間にない場所、種類と区間の位置が合わない実績は結ばない。 */
    @ParameterizedTest(name = "行5 {0}")
    @CsvSource({
        "区間にない場所, DEPARTURE CNSHA 2026-11-01T01:00:00Z",
        "到着・KRPUS は最後の区間の揚地でない, ARRIVAL KRPUS 2026-11-03T01:00:00Z",
        "積替・USLAX は最後の区間の揚地, TRANSSHIPMENT USLAX 2026-11-15T01:00:00Z"
    })
    void Bolt39g4_行5_結ばない実績は次の予定の実績を進めない(String row, String adopted) {
        assertThat(describe(rule.next(schedule(), adoptedMilestones(adopted)))).isEqualTo(D1);
    }

    /** 行 5。同じ港を 2 度使う予定の出発・JPTYO は 2 つの区間に当たるので結ばない（39g3 の決定 (3)）。 */
    @Test
    void Bolt39g4_行5_同じ場所が2つ以上の区間に当たる実績は結ばない() {
        Schedule looped = new Schedule(
                "RC-2026-0001",
                1,
                List.of(
                        leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z"),
                        leg("V200", "KRPUS", "JPTYO", "2026-11-04T00:00:00Z", "2026-11-06T00:00:00Z"),
                        leg("V300", "JPTYO", "USLAX", "2026-11-07T00:00:00Z", "2026-11-15T00:00:00Z")));

        assertThat(describe(rule.next(looped, adoptedMilestones("DEPARTURE JPTYO 2026-11-07T01:00:00Z"))))
                .isEqualTo(D1);
    }

    private static String describe(Optional<SchedulePoint> point) {
        return point.map(found -> found.kind() + " " + found.location().unLocode() + " "
                        + found.scheduledAt().instant())
                .orElse(NONE);
    }

    private static List<Milestone> adoptedMilestones(String spec) {
        List<Milestone> milestones = new ArrayList<>();
        if (spec.isBlank()) {
            return milestones;
        }
        for (String each : spec.split(";")) {
            String[] parts = each.strip().split(" ");
            int no = milestones.size() + 1;
            milestones.add(new Milestone(
                    no,
                    MilestoneKind.valueOf(parts[0]),
                    new Location(parts[1]),
                    at(parts[2]),
                    source(no),
                    MilestoneState.ADOPTED,
                    REGISTRANT,
                    at("2026-11-16T00:00:00Z")));
        }
        return milestones;
    }

    private static Source source(int no) {
        return new Source(SourceKind.FIELD_RECORD, "F-" + no, at("2026-11-16T00:00:00Z"));
    }
}
