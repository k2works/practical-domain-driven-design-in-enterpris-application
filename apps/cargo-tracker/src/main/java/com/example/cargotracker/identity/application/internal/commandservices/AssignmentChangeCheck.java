package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.AssignmentChangeRejection;
import java.util.Objects;

/**
 * 担当範囲の変更を受け付けられるかを確かめた結果（{@link UserAssignmentService#check}。Bolt 38e）。何も書いていない。画面は受け付けられれば
 * 確認の領域を開き、受け付けなければ拒否の種類で行き先と文言を選ぶ（前例 {@link RoleChangeCheck}）。
 */
public sealed interface AssignmentChangeCheck {

    /** この変更を受け付けられる。 */
    record Acceptable() implements AssignmentChangeCheck {}

    /**
     * 受け付けない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserAdministrationRefusal<AssignmentChangeRejection> refusal) implements AssignmentChangeCheck {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
