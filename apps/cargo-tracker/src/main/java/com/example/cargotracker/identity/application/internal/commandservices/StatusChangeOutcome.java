package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import java.util.Objects;

/**
 * 利用状態の変更の結果（{@link UserStatusChangeService#change}。Bolt 34d。Bolt 34e で確かめる結果と分けた）。画面は変えたなら詳細へ戻し、受け付けなければ拒否の種類で行き先と文言を選ぶ。
 */
public sealed interface StatusChangeOutcome {

    /**
     * 利用状態を変えた（版を上げ、監査記録を書いた）。
     *
     * @param version 更新した後の版
     */
    record Changed(long version) implements StatusChangeOutcome {}

    /**
     * 受け付けない。何も書いていない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserAdministrationRefusal<StatusChangeRejection> refusal) implements StatusChangeOutcome {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
