package com.example.cargotracker.tracking.domain.model.rules;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 時系列の規則（Bolt 39gc の行 1・2。【業務の判断】(ag)）。段の向きと発生時刻の向きが採用済みの実績と逆なら反する。 */
class MilestoneSequenceRuleTest {

    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-0000039c0001"));
    private static final UserId ADOPTER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000039c0002"));

    private final MilestoneSequenceRule rule = new MilestoneSequenceRule();

    /** 行 1。採用済みの実績 A と新しい実績 B の組（入力と期待の組まで縛る。T-244）。 */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "(a) 段が後で発生が前, DEPARTURE, 2026-11-10T00:00:00Z, ARRIVAL, 2026-11-09T00:00:00Z, true",
        "(b) 段が前で発生が後, ARRIVAL, 2026-11-10T00:00:00Z, PICKUP, 2026-11-11T00:00:00Z, true",
        "(c) 段が後で発生が後, DEPARTURE, 2026-11-10T00:00:00Z, ARRIVAL, 2026-11-11T00:00:00Z, false",
        "(d) 段が前で発生が前（遅れて届いた順どおり）, ARRIVAL, 2026-11-10T00:00:00Z, PICKUP, 2026-11-09T00:00:00Z, false",
        "(e) 発生が同じ時刻, DEPARTURE, 2026-11-10T00:00:00Z, PICKUP, 2026-11-10T00:00:00Z, false",
        "(f) 出発の後に前の時刻の積替（同じ段）, DEPARTURE, 2026-11-10T00:00:00Z, TRANSSHIPMENT, 2026-11-09T00:00:00Z, false",
        "(f) 積替の後に前の時刻の出発（同じ段）, TRANSSHIPMENT, 2026-11-10T00:00:00Z, DEPARTURE, 2026-11-09T00:00:00Z, false",
        "段の 1 と 2: 搬入の後に前の時刻の集荷は反しない, RECEIPT_AT_ORIGIN, 2026-11-10T00:00:00Z, PICKUP, 2026-11-09T00:00:00Z, false",
        "段の 4 と 5: 到着の後に前の時刻の引渡し, ARRIVAL, 2026-11-10T00:00:00Z, DELIVERY, 2026-11-09T00:00:00Z, true",
        "段の 2 と 3: 出発の後に後の時刻の搬入, DEPARTURE, 2026-11-10T00:00:00Z, RECEIPT_AT_ORIGIN, 2026-11-11T00:00:00Z, true"
    })
    void 行1_段の向きと発生時刻の向きが逆なら時系列に反する(
            String name,
            MilestoneKind adoptedKind,
            String adoptedAt,
            MilestoneKind newKind,
            String newAt,
            boolean out) {
        Milestone adopted = manual(1, adoptedKind, adoptedAt, MilestoneState.ADOPTED);
        Milestone candidate = manual(2, newKind, newAt, MilestoneState.ADOPTED);

        assertThat(rule.isOutOfSequence(candidate, List.of(adopted))).isEqualTo(out);
    }

    /** 行 2。下書き・保持のみ・確認中とは比べない。 */
    @ParameterizedTest(name = "{0}")
    @CsvSource({"DRAFT", "RETAINED_ONLY", "UNDER_REVIEW"})
    void 行2_採用済みでない実績とは比べない(MilestoneState state) {
        Milestone other = state == MilestoneState.DRAFT
                ? external(1, MilestoneKind.ARRIVAL, "2026-11-10T00:00:00Z", MilestoneState.DRAFT)
                : manual(1, MilestoneKind.ARRIVAL, "2026-11-10T00:00:00Z", state);
        Milestone candidate = manual(2, MilestoneKind.PICKUP, "2026-11-11T00:00:00Z", MilestoneState.ADOPTED);

        assertThat(rule.isOutOfSequence(candidate, List.of(other))).isFalse();
    }

    /** 行 2。出典をまたぐ（外部原本の採用済みと手入力）。 */
    @Test
    void 行2_外部原本の採用済みの実績とも比べる() {
        Milestone adoptedExternal = external(1, MilestoneKind.ARRIVAL, "2026-11-10T00:00:00Z", MilestoneState.ADOPTED);
        Milestone candidate = manual(2, MilestoneKind.PICKUP, "2026-11-11T00:00:00Z", MilestoneState.ADOPTED);

        assertThat(rule.isOutOfSequence(candidate, List.of(adoptedExternal))).isTrue();
    }

    /** 行 2。採用済みの実績の 1 つでも逆なら反する。 */
    @Test
    void 行2_採用済みの実績の1つでも逆なら反する() {
        List<Milestone> adopted = List.of(
                manual(1, MilestoneKind.PICKUP, "2026-11-08T00:00:00Z", MilestoneState.ADOPTED),
                manual(2, MilestoneKind.ARRIVAL, "2026-11-12T00:00:00Z", MilestoneState.ADOPTED));
        Milestone candidate = manual(3, MilestoneKind.DELIVERY, "2026-11-11T00:00:00Z", MilestoneState.ADOPTED);

        assertThat(rule.isOutOfSequence(candidate, adopted)).isTrue();
    }

    @Test
    void 採用済みの実績がなければ反しない() {
        Milestone candidate = manual(1, MilestoneKind.DELIVERY, "2026-11-11T00:00:00Z", MilestoneState.ADOPTED);

        assertThat(rule.isOutOfSequence(candidate, List.of())).isFalse();
    }

    private static Milestone manual(int no, MilestoneKind kind, String occurredAt, MilestoneState state) {
        return new Milestone(
                no,
                kind,
                new Location("JPTYO"),
                at(occurredAt),
                new Source(SourceKind.FIELD_RECORD, "F-" + no, at("2026-11-12T00:00:00Z")),
                state,
                REGISTRANT,
                at("2026-11-12T00:00:00Z"));
    }

    private static Milestone external(int no, MilestoneKind kind, String occurredAt, MilestoneState state) {
        boolean adopted = state == MilestoneState.ADOPTED;
        return new Milestone(
                no,
                kind,
                new Location("JPTYO"),
                at(occurredAt),
                new Source(SourceKind.EXTERNAL_RECORD, "r-" + no, at("2026-11-12T00:00:00Z")),
                state,
                REGISTRANT,
                at("2026-11-12T00:00:00Z"),
                new ExternalOrigin(
                        UUID.fromString("00000000-0000-0000-0000-0000039c0003"),
                        "MS-" + no,
                        at("2026-11-12T00:00:00Z")),
                adopted ? ADOPTER : null,
                adopted ? at("2026-11-12T01:00:00Z") : null);
    }
}
