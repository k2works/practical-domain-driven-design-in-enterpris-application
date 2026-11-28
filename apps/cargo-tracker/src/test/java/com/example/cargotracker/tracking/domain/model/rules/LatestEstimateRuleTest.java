package com.example.cargotracker.tracking.domain.model.rules;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.leg;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 最新の到着見込みの規則（T-INV-10。Bolt 39g3 の状態の組み合わせの行 1〜6。【業務の判断】(ao)）。予定は 2 区間（区間 1: JPTYO から KRPUS、
 * 出発予定 2026-11-01 00:00、到着予定 2026-11-03 00:00。区間 2: KRPUS から USLAX、出発予定 2026-11-04 00:00、到着予定 2026-11-15 00:00。UTC）。
 */
class LatestEstimateRuleTest {

    private static final UtcInstant ORIGINAL = at("2026-11-15T00:00:00Z");
    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-000000039301"));

    private final LatestEstimateRule rule = new LatestEstimateRule();

    /** 行 1。対応づけの入力と期待の組（T-244）。発生時刻は結ぶ予定時刻の 6 時間後なので、結べば当初の到着予定から動く。 */
    @ParameterizedTest(name = "行1 {0}: {1} {2} {3} → {4}")
    @CsvSource({
        "(a) 出発・JPTYO は区間 1 の出発予定, DEPARTURE, JPTYO, 2026-11-01T06:00:00Z, 2026-11-15T06:00:00Z",
        "(b) 出発・KRPUS は区間 2 の出発予定, DEPARTURE, KRPUS, 2026-11-04T06:00:00Z, 2026-11-15T06:00:00Z",
        "(c) 積替・KRPUS は区間 1 の到着予定, TRANSSHIPMENT, KRPUS, 2026-11-03T06:00:00Z, 2026-11-15T06:00:00Z",
        "(d) 到着・USLAX は区間 2 の到着予定で実績の時刻, ARRIVAL, USLAX, 2026-11-15T06:00:00Z, 2026-11-15T06:00:00Z",
        "(e) 集荷は結ばない, PICKUP, JPTYO, 2026-11-01T06:00:00Z, 2026-11-15T00:00:00Z",
        "(e) 搬入は結ばない, RECEIPT_AT_ORIGIN, JPTYO, 2026-11-01T06:00:00Z, 2026-11-15T00:00:00Z",
        "(e) 引渡しは結ばない, DELIVERY, USLAX, 2026-11-15T06:00:00Z, 2026-11-15T00:00:00Z",
        "(f) 区間にない場所は結ばない, DEPARTURE, CNSHA, 2026-11-01T06:00:00Z, 2026-11-15T00:00:00Z",
        "(g) 到着・KRPUS は最後の区間の揚地でないので結ばない, ARRIVAL, KRPUS, 2026-11-03T06:00:00Z, 2026-11-15T00:00:00Z",
        "(g) 積替・USLAX は最後の区間の揚地なので結ばない, TRANSSHIPMENT, USLAX, 2026-11-15T06:00:00Z, 2026-11-15T00:00:00Z",
        "(g) 出発・USLAX は積地にないので結ばない, DEPARTURE, USLAX, 2026-11-15T06:00:00Z, 2026-11-15T00:00:00Z"
    })
    void 行1_実績の種類と場所で予定区間の時刻に結ぶ(String row, MilestoneKind kind, String location, String occurredAt, String expected) {
        assertThat(rule.estimate(schedule(), List.of(adopted(1, kind, location, occurredAt))))
                .isEqualTo(at(expected));
    }

    /** 行 1 の守り。同じ場所が 2 つ以上の区間の積地に当たる出発は結ばない（予定の規則は同じ港を 2 度使うことを禁じていない）。 */
    @Test
    void 行1_同じ場所が2つ以上の区間に当たる実績は結ばない() {
        Schedule looped = new Schedule(
                "RC-2026-0001",
                1,
                List.of(
                        leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z"),
                        leg("V200", "KRPUS", "JPTYO", "2026-11-04T00:00:00Z", "2026-11-06T00:00:00Z"),
                        leg("V300", "JPTYO", "USLAX", "2026-11-07T00:00:00Z", "2026-11-15T00:00:00Z")));

        assertThat(rule.estimate(looped, List.of(adopted(1, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-07T06:00:00Z"))))
                .isEqualTo(ORIGINAL);
    }

    /** 行 2。出発・積替の遅れ。遅れだけを先へ送り、早い出発で見込みを早めない（本計画 CP-3）。 */
    @ParameterizedTest(name = "行2 {0}")
    @CsvSource({
        "(a) 6 時間後, DEPARTURE, JPTYO, 2026-11-01T06:00:00Z, 2026-11-15T06:00:00Z",
        "(b) ちょうど, DEPARTURE, JPTYO, 2026-11-01T00:00:00Z, 2026-11-15T00:00:00Z",
        "(c) 3 時間前, DEPARTURE, JPTYO, 2026-10-31T21:00:00Z, 2026-11-15T00:00:00Z",
        "(a) 積替の 6 時間後, TRANSSHIPMENT, KRPUS, 2026-11-03T06:00:00Z, 2026-11-15T06:00:00Z",
        "(b) 積替のちょうど, TRANSSHIPMENT, KRPUS, 2026-11-03T00:00:00Z, 2026-11-15T00:00:00Z",
        "(c) 積替の 3 時間前, TRANSSHIPMENT, KRPUS, 2026-11-02T21:00:00Z, 2026-11-15T00:00:00Z"
    })
    void 行2_出発と積替は正の遅れだけを当初の到着予定に足す(
            String row, MilestoneKind kind, String location, String occurredAt, String expected) {
        assertThat(rule.estimate(schedule(), List.of(adopted(1, kind, location, occurredAt))))
                .isEqualTo(at(expected));
    }

    /** 行 3。到着（最後の区間の揚地）の実績なら見込みは実績の発生時刻（早着も実績にする）。 */
    @ParameterizedTest(name = "行3 {0}")
    @CsvSource({"(a) 遅い, 2026-11-15T09:00:00Z", "(b) 同じ, 2026-11-15T00:00:00Z", "(c) 早い, 2026-11-14T18:00:00Z"})
    void 行3_到着の実績なら見込みは実績の発生時刻(String row, String occurredAt) {
        assertThat(rule.estimate(schedule(), List.of(adopted(1, MilestoneKind.ARRIVAL, "USLAX", occurredAt))))
                .isEqualTo(at(occurredAt));
    }

    /** 行 4 (a)。予定の順で最も後の実績を使う（積替港で遅れを取り戻した）。 */
    @Test
    void 行4a_予定の順で最も後の実績の遅れを使う() {
        List<Milestone> milestones = List.of(
                adopted(1, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z"),
                adopted(2, MilestoneKind.DEPARTURE, "KRPUS", "2026-11-04T00:00:00Z"));

        assertThat(rule.estimate(schedule(), milestones)).isEqualTo(ORIGINAL);
    }

    /** 行 4 (a) の逆の登録の順。実績番号の順でなく予定の順で選ぶ。 */
    @Test
    void 行4a_登録の順によらず予定の順で選ぶ() {
        List<Milestone> milestones = List.of(
                adopted(1, MilestoneKind.DEPARTURE, "KRPUS", "2026-11-04T02:00:00Z"),
                adopted(2, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z"));

        assertThat(rule.estimate(schedule(), milestones)).isEqualTo(at("2026-11-15T02:00:00Z"));
    }

    /** 行 4 (b)。同じ予定時刻に 2 つの出典の出発があれば発生時刻の遅い方。 */
    @Test
    void 行4b_同じ予定時刻なら発生時刻の遅い実績を使う() {
        List<Milestone> milestones = List.of(
                adopted(1, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z"),
                adopted(2, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T04:00:00Z"));

        assertThat(rule.estimate(schedule(), milestones)).isEqualTo(at("2026-11-15T06:00:00Z"));
    }

    /**
     * Bolt 39g4 の行 2。積替予定（区間 1 の到着予定）と区間 2 の出発予定が同じ時刻の予定で、積替・KRPUS（6 時間遅れ）と区間 2 の出発・KRPUS
     * （2 時間遅れ）を採用した組。比べ方は予定時刻の値で、同じなら発生時刻の遅い方（積替。当初 + 6 時間）。予定の時点の列の位置で比べると区間 2 の
     * 出発（当初 + 2 時間）になるので、リファクタリングが比べ方を変えていないことを縛る（変異 M15）。
     */
    @Test
    void Bolt39g4_行2_同じ予定時刻の積替と次の区間の出発は発生時刻の遅い方を使う() {
        Schedule sameTime = new Schedule(
                "RC-2026-0001",
                1,
                List.of(
                        leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z"),
                        leg("V200", "KRPUS", "USLAX", "2026-11-03T00:00:00Z", "2026-11-15T00:00:00Z")));
        List<Milestone> milestones = List.of(
                adopted(1, MilestoneKind.TRANSSHIPMENT, "KRPUS", "2026-11-03T06:00:00Z"),
                adopted(2, MilestoneKind.DEPARTURE, "KRPUS", "2026-11-03T02:00:00Z"));

        assertThat(rule.estimate(sameTime, milestones)).isEqualTo(at("2026-11-15T06:00:00Z"));
    }

    /** 行 5。採用だけが見込みを動かす（T-INV-08 と同じく現在状態に効く実績だけ）。 */
    @ParameterizedTest(name = "行5 {0}")
    @EnumSource(MilestoneState.class)
    void 行5_採用の実績だけが見込みを動かす(MilestoneState state) {
        Milestone milestone = new Milestone(
                1,
                MilestoneKind.DEPARTURE,
                new Location("JPTYO"),
                at("2026-11-01T06:00:00Z"),
                source("F-1"),
                state,
                REGISTRANT,
                at("2026-11-02T00:00:00Z"));

        assertThat(rule.estimate(schedule(), List.of(milestone)))
                .isEqualTo(state == MilestoneState.ADOPTED ? at("2026-11-15T06:00:00Z") : ORIGINAL);
    }

    /** 行 6。対応する採用の実績がなければ当初の到着予定。 */
    @Test
    void 行6_実績がなければ当初の到着予定() {
        assertThat(rule.estimate(schedule(), List.of())).isEqualTo(ORIGINAL);
    }

    @Test
    void 行6_集荷だけなら当初の到着予定() {
        assertThat(rule.estimate(
                        schedule(), List.of(adopted(1, MilestoneKind.PICKUP, "JPTYO", "2026-11-01T06:00:00Z"))))
                .isEqualTo(ORIGINAL);
    }

    /**
     * Bolt 39g4b の行 3。採用の実績と航海の到着予定の組（【業務の判断】(at)、D-136）。到着の実績があれば実績、なければ実績の遅れの見込みと航海の
     * 到着予定の遅い方。当初の到着予定（A2 = 2026-11-15 00:00）より早めない。確認中は使わない。
     */
    @org.junit.jupiter.api.Nested
    class Bolt39g4b_行3_航海の到着予定 {

        @Test
        void a_航海の到着予定がなければ39g3と同じ() {
            List<Milestone> milestones = List.of(adopted(1, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z"));

            assertThat(rule.estimate(schedule(), milestones, java.util.Optional.empty()))
                    .isEqualTo(rule.estimate(schedule(), milestones))
                    .isEqualTo(at("2026-11-15T06:00:00Z"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "(b) 実績なし・航海 A2 + 10 時間, , , 2026-11-15T10:00:00Z, 2026-11-15T10:00:00Z",
            "(c) 実績なし・航海 A2 − 5 時間（早着の知らせ）, , , 2026-11-14T19:00:00Z, 2026-11-15T00:00:00Z",
            "(d) 区間 1 の出発 D1 + 6 時間・航海 A2 + 3 時間, DEPARTURE, 2026-11-01T06:00:00Z, 2026-11-15T03:00:00Z, 2026-11-15T06:00:00Z",
            "(e) 区間 1 の出発 D1 + 6 時間・航海 A2 + 8 時間, DEPARTURE, 2026-11-01T06:00:00Z, 2026-11-15T08:00:00Z, 2026-11-15T08:00:00Z",
            "(f) 到着・USLAX の実績 A2 + 1 時間・航海 A2 + 10 時間, ARRIVAL, 2026-11-15T01:00:00Z, 2026-11-15T10:00:00Z, 2026-11-15T01:00:00Z",
            "(h) 区間 1 の出発 D1 + 6 時間・航海 A2 + 6 時間, DEPARTURE, 2026-11-01T06:00:00Z, 2026-11-15T06:00:00Z, 2026-11-15T06:00:00Z"
        })
        void 採用の実績と航海の到着予定の遅い方(
                String row, MilestoneKind kind, String occurredAt, String voyageArrival, String expected) {
            List<Milestone> milestones = kind == null
                    ? List.of()
                    : List.of(adopted(1, kind, kind == MilestoneKind.ARRIVAL ? "USLAX" : "JPTYO", occurredAt));

            assertThat(rule.estimate(schedule(), milestones, java.util.Optional.of(at(voyageArrival))))
                    .isEqualTo(at(expected));
        }

        @Test
        void g_確認中の到着は使わず航海の到着予定() {
            Milestone underReview = new Milestone(
                    1,
                    MilestoneKind.ARRIVAL,
                    new Location("USLAX"),
                    at("2026-11-15T01:00:00Z"),
                    source("F-1"),
                    MilestoneState.UNDER_REVIEW,
                    REGISTRANT,
                    at("2026-11-16T00:00:00Z"));

            assertThat(rule.estimate(
                            schedule(), List.of(underReview), java.util.Optional.of(at("2026-11-15T10:00:00Z"))))
                    .isEqualTo(at("2026-11-15T10:00:00Z"));
        }
    }

    /**
     * Bolt 39g4c の行 3。規則が見込みと根拠を 1 回の評価で返す（計画 決定 (4)・(5)）。見込みは 39g3・39g4b の同じ組と同じ値。実績は
     * 「種類:場所:発生時刻:状態」を「;」でつなぎ、実績番号は並びの順に 1 から。根拠は種類と実績番号（なければ空）。
     */
    @org.junit.jupiter.api.Nested
    class Bolt39g4c_行3_見込みの根拠 {

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "(a) 実績なし・航海なし | | | 2026-11-15T00:00:00Z | ORIGINAL_ETA |",
                    "(b) 区間 1 の出発 D1 + 6 時間 | DEPARTURE:JPTYO:2026-11-01T06:00:00Z:ADOPTED | | 2026-11-15T06:00:00Z | MILESTONE | 1",
                    "(c) 区間 1 の早い出発 D1 − 1 時間 | DEPARTURE:JPTYO:2026-10-31T23:00:00Z:ADOPTED | | 2026-11-15T00:00:00Z | MILESTONE | 1",
                    "(d) 到着・USLAX A2 + 1 時間 | ARRIVAL:USLAX:2026-11-15T01:00:00Z:ADOPTED | | 2026-11-15T01:00:00Z | MILESTONE | 1",
                    "(e) 実績なし・航海 A2 + 10 時間 | | 2026-11-15T10:00:00Z | 2026-11-15T10:00:00Z | VOYAGE_ARRIVAL |",
                    "(f) 実績なし・航海 A2 − 5 時間 | | 2026-11-14T19:00:00Z | 2026-11-15T00:00:00Z | ORIGINAL_ETA |",
                    "(g) 出発 D1 + 6 時間・航海 A2 + 3 時間 | DEPARTURE:JPTYO:2026-11-01T06:00:00Z:ADOPTED | 2026-11-15T03:00:00Z | 2026-11-15T06:00:00Z | MILESTONE | 1",
                    "(h) 出発 D1 + 6 時間・航海 A2 + 8 時間 | DEPARTURE:JPTYO:2026-11-01T06:00:00Z:ADOPTED | 2026-11-15T08:00:00Z | 2026-11-15T08:00:00Z | VOYAGE_ARRIVAL |",
                    "(i) 出発 D1 + 6 時間・航海 A2 + 6 時間（同じ時刻） | DEPARTURE:JPTYO:2026-11-01T06:00:00Z:ADOPTED | 2026-11-15T06:00:00Z | 2026-11-15T06:00:00Z | MILESTONE | 1",
                    "(j) 到着 A2 + 1 時間・航海 A2 + 10 時間 | ARRIVAL:USLAX:2026-11-15T01:00:00Z:ADOPTED | 2026-11-15T10:00:00Z | 2026-11-15T01:00:00Z | MILESTONE | 1",
                    "(k) 確認中の出発だけ | DEPARTURE:JPTYO:2026-11-01T06:00:00Z:UNDER_REVIEW | | 2026-11-15T00:00:00Z | ORIGINAL_ETA |",
                    "(l) 区間 1 の出発の遅れと区間 2 の定刻の出発 | DEPARTURE:JPTYO:2026-11-01T06:00:00Z:ADOPTED;DEPARTURE:KRPUS:2026-11-04T00:00:00Z:ADOPTED | | 2026-11-15T00:00:00Z | MILESTONE | 2",
                    "(m) 区間 1 の積替 A1 + 3 時間 | TRANSSHIPMENT:KRPUS:2026-11-03T03:00:00Z:ADOPTED | | 2026-11-15T03:00:00Z | MILESTONE | 1",
                    "(n) 集荷だけ | PICKUP:JPTYO:2026-10-31T00:00:00Z:ADOPTED | | 2026-11-15T00:00:00Z | ORIGINAL_ETA |",
                    "(o) 実績なし・航海ちょうど A2 | | 2026-11-15T00:00:00Z | 2026-11-15T00:00:00Z | ORIGINAL_ETA |"
                })
        void 見込みと根拠(
                String row,
                String milestones,
                String voyageArrival,
                String expectedAt,
                com.example.cargotracker.tracking.domain.model.valueobjects.LatestEstimateBasis.Kind expectedKind,
                Integer expectedNo) {
            java.util.Optional<UtcInstant> voyage =
                    voyageArrival == null ? java.util.Optional.empty() : java.util.Optional.of(at(voyageArrival));
            List<Milestone> parsed = parse(milestones);

            com.example.cargotracker.tracking.domain.model.valueobjects.LatestEstimate estimate =
                    rule.estimateWithBasis(schedule(), parsed, voyage);

            assertThat(estimate.at()).isEqualTo(at(expectedAt)).isEqualTo(rule.estimate(schedule(), parsed, voyage));
            assertThat(estimate.basis())
                    .contains(new com.example.cargotracker.tracking.domain.model.valueobjects.LatestEstimateBasis(
                            expectedKind,
                            expectedNo == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(expectedNo)));
        }

        private static List<Milestone> parse(String milestones) {
            if (milestones == null) {
                return List.of();
            }
            String[] specs = milestones.split(";");
            List<Milestone> parsed = new java.util.ArrayList<>();
            for (int i = 0; i < specs.length; i++) {
                String[] parts = specs[i].split(":", 3);
                String occurredAt = parts[2].substring(0, parts[2].lastIndexOf(':'));
                MilestoneState state = MilestoneState.valueOf(parts[2].substring(parts[2].lastIndexOf(':') + 1));
                parsed.add(new Milestone(
                        i + 1,
                        MilestoneKind.valueOf(parts[0]),
                        new Location(parts[1]),
                        at(occurredAt),
                        source("F-" + (i + 1)),
                        state,
                        REGISTRANT,
                        at("2026-11-16T00:00:00Z")));
            }
            return parsed;
        }
    }

    private static Milestone adopted(int no, MilestoneKind kind, String location, String occurredAt) {
        return new Milestone(
                no,
                kind,
                new Location(location),
                at(occurredAt),
                source("F-" + no),
                MilestoneState.ADOPTED,
                REGISTRANT,
                at("2026-11-16T00:00:00Z"));
    }

    private static Source source(String reference) {
        return new Source(SourceKind.FIELD_RECORD, reference, at("2026-11-16T00:00:00Z"));
    }
}
