package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.Objects;

/** 下書きの候補の採用の結果（US-12 AC1・AC2 の外部の分。Bolt 39gb）。画面が結果ごとの文言を示す。 */
public sealed interface MilestoneAdoptionOutcome {

    /**
     * 採用した。現在状態を導出し直した。
     *
     * @param milestoneNo 採用した実績の実績番号
     * @param currentStatus 導出し直した現在状態
     */
    record Adopted(int milestoneNo, TrackingStatus currentStatus) implements MilestoneAdoptionOutcome {

        public Adopted {
            Objects.requireNonNull(currentStatus, "currentStatus");
        }
    }

    /**
     * 候補と同じ値の実績が採用済みで、何もしなかった。
     *
     * @param adoptedMilestoneNo 採用済みの実績の実績番号
     */
    record SameAsAdopted(int adoptedMilestoneNo) implements MilestoneAdoptionOutcome {}

    /**
     * 採用を拒否した。
     *
     * @param reason 理由
     */
    record Rejected(AdoptionRejectionReason reason) implements MilestoneAdoptionOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 追跡番号の追跡記録がない。 */
    record NotFound() implements MilestoneAdoptionOutcome {}

    /** 追跡記録の荷主企業が操作者の担当企業でない（Bolt 38g の前例）。何も書かずに返す。 */
    record OutOfScope() implements MilestoneAdoptionOutcome {}

    /** 画面を開いた後に、ほかの更新（追跡管理者の登録・採用、DE-13 の受け取り）が先にこの追跡記録を変更した（版の不一致・楽観ロックの競合）。 */
    record Conflict() implements MilestoneAdoptionOutcome {}
}
