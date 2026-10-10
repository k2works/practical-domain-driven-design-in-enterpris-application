package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.Objects;

/**
 * 主要実績の登録の結果（US-12 AC1・AC2。Bolt 26b）。画面が結果ごとの文言を示す（Bolt 26c）。
 */
public sealed interface MilestoneRegistrationOutcome {

    /**
     * 登録した。現在状態を導出し直した（AC1）。
     *
     * @param milestoneNo 登録した実績の実績番号
     * @param currentStatus 導出し直した現在状態
     */
    record Registered(int milestoneNo, TrackingStatus currentStatus) implements MilestoneRegistrationOutcome {

        public Registered {
            Objects.requireNonNull(currentStatus, "currentStatus");
        }
    }

    /**
     * 同じ出典識別子（出典の種類と参照）の実績が登録済みで、新しい実績を作らなかった（AC2、T-INV-02）。
     *
     * @param milestoneNo 既存の実績の実績番号
     */
    record AlreadyRegistered(int milestoneNo) implements MilestoneRegistrationOutcome {}

    /**
     * 登録を拒否した。
     *
     * @param reason 理由
     */
    record Rejected(MilestoneRejectionReason reason) implements MilestoneRegistrationOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 追跡番号の追跡記録がない。 */
    record NotFound() implements MilestoneRegistrationOutcome {}

    /** 画面を開いた後に、ほかの追跡管理者が先に追跡記録を更新した（版の不一致・楽観ロックの競合）。 */
    record Conflict() implements MilestoneRegistrationOutcome {}
}
