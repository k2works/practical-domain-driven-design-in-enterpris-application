package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.AssignmentChangeRejection;
import java.util.Objects;

/**
 * 担当範囲の変更の結果（{@link UserAssignmentService#change}。Bolt 38e）。画面は変えたなら詳細へ戻し、受け付けなければ拒否の種類で行き先と
 * 文言を選ぶ（前例 {@link RoleChangeOutcome}）。
 */
public sealed interface AssignmentChangeOutcome {

    /**
     * 担当企業を変えた（版を上げ、監査記録を書いた）。
     *
     * @param version 更新した後の版
     */
    record Changed(long version) implements AssignmentChangeOutcome {}

    /**
     * 受け付けない。何も書いていない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserAdministrationRefusal<AssignmentChangeRejection> refusal) implements AssignmentChangeOutcome {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
