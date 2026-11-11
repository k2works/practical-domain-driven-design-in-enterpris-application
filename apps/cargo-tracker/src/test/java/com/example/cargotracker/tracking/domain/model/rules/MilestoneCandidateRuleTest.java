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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 候補の規則（Bolt 39gb の行 5。公開した時刻 → 取得時刻 → 受信記録 ID の文字列の順）。 */
class MilestoneCandidateRuleTest {

    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-0000039b0001");
    private static final UserId STEWARD = new UserId(UUID.fromString("00000000-0000-0000-0000-0000039b0002"));

    private final MilestoneCandidateRule rule = new MilestoneCandidateRule();

    private static Milestone draft(int no, String publishedAt, String acquiredAt, String receivedRecordId) {
        return new Milestone(
                no,
                MilestoneKind.DEPARTURE,
                new Location("JPTYO"),
                at("2026-11-19T00:00:00Z"),
                new Source(SourceKind.EXTERNAL_RECORD, receivedRecordId, at(acquiredAt)),
                MilestoneState.DRAFT,
                STEWARD,
                at(publishedAt),
                new ExternalOrigin(SOURCE_ID, "MS-0001", at(publishedAt)));
    }

    /** 入力と期待の組まで縛る（T-244）。並びを逆にしても同じ実績が最も新しい（T-245）。 */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "公開した時刻が新しい, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, b, 2026-11-19T11:00:00Z, 2026-11-19T08:00:00Z, a, 2",
        "公開した時刻が同じで取得時刻が新しい, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, b, 2026-11-19T10:00:00Z, 2026-11-19T09:30:00Z, a, 2",
        "公開した時刻が新しければ取得時刻が古くても新しい, 2026-11-19T10:00:00Z, 2026-11-19T09:30:00Z, b, 2026-11-19T10:00:01Z, 2026-11-19T09:00:00Z, a, 2",
        "両方同じで受信記録 ID が大きい, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, a, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, b, 2",
        "取得時刻が新しければ受信記録 ID が小さくても新しい, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, b, 2026-11-19T10:00:00Z, 2026-11-19T09:00:01Z, a, 2",
        "実績番号の大きさは比べない, 2026-11-19T11:00:00Z, 2026-11-19T09:00:00Z, a, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, b, 1"
    })
    void 行5_最も新しい実績を順の規則で決める(
            String name,
            String published1,
            String acquired1,
            String ref1,
            String published2,
            String acquired2,
            String ref2,
            int expectedNewest) {
        Milestone first = draft(1, published1, acquired1, ref1);
        Milestone second = draft(2, published2, acquired2, ref2);

        assertThat(rule.newest(List.of(first, second)).orElseThrow().milestoneNo()).isEqualTo(expectedNewest);
        assertThat(rule.newest(List.of(second, first)).orElseThrow().milestoneNo()).isEqualTo(expectedNewest);
    }

    @org.junit.jupiter.api.Test
    void 空の集まりには最も新しい実績がない() {
        assertThat(rule.newest(List.of())).isEmpty();
    }
}
