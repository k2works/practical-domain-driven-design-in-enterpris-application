package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.LoginCredentialRejection;
import java.util.Objects;

/**
 * ロックの解除を受け付けられるかを確かめた結果（{@link UserLoginCredentialService#checkUnlock}。Bolt 38c）。何も書いていない。画面は
 * 受け付けられれば確認の画面を開き、受け付けなければ拒否の種類で行き先と文言を選ぶ。
 */
public sealed interface UnlockCheck {

    /** ロック中で、解除を受け付けられる。 */
    record Acceptable() implements UnlockCheck {}

    /**
     * 受け付けない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserAdministrationRefusal<LoginCredentialRejection> refusal) implements UnlockCheck {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
