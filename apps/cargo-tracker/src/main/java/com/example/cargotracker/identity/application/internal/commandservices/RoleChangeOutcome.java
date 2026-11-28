package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import java.util.Objects;

/**
 * 役割の変更の結果（{@link UserRoleChangeService#change}。Bolt 34。Bolt 34e で確かめる結果と分けた）。画面は変えたなら詳細へ戻し、受け付けなければ拒否の種類で行き先と文言を選ぶ。
 */
public sealed interface RoleChangeOutcome {

    /**
     * 役割を変えた（版を上げ、監査記録を書いた）。
     *
     * @param version 更新した後の版
     */
    record Changed(long version) implements RoleChangeOutcome {}

    /**
     * 受け付けない。何も書いていない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserAdministrationRefusal<RoleChangeRejection> refusal) implements RoleChangeOutcome {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
