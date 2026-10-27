package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.LoginCredentialRejection;
import java.util.Objects;

/**
 * ロックの解除の結果（{@link UserLoginCredentialService#unlock}。Bolt 38c）。画面は解除したなら詳細へ戻し、受け付けなければ拒否の種類で
 * 行き先と文言を選ぶ。
 */
public sealed interface UnlockOutcome {

    /** ロックを解除した（0 回に戻し、監査記録を書いた）。 */
    record Unlocked() implements UnlockOutcome {}

    /**
     * 受け付けない。何も書いていない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserAdministrationRefusal<LoginCredentialRejection> refusal) implements UnlockOutcome {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
