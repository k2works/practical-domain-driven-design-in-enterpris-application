package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import java.util.Objects;

/**
 * 役割の変更を受け付けられるかを確かめた結果（{@link UserRoleChangeService#check}。Bolt 34e）。何も書いていない。画面は受け付けられれば確認の領域を開き、
 * 受け付けなければ拒否の種類で行き先と文言を選ぶ。
 */
public sealed interface RoleChangeCheck {

    /** この変更を受け付けられる。 */
    record Acceptable() implements RoleChangeCheck {}

    /**
     * 受け付けない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserAdministrationRefusal<RoleChangeRejection> refusal) implements RoleChangeCheck {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
