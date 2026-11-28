package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.LoginCredentialRejection;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 管理者による TOTP の解除を受け付けられるかを確かめた結果（{@link UserLoginCredentialService#checkTotpReset}。Bolt 38c）。何も書いて
 * いない。画面は受け付けられれば確認の画面を開き（読んだ登録の時刻を持つ）、受け付けなければ拒否の種類で行き先と文言を選ぶ。
 */
public sealed interface TotpResetCheck {

    /**
     * 登録済みで、解除を受け付けられる。
     *
     * @param enrolledAt 読んだ登録の時刻（確定のときに、この登録のときだけ消す）
     */
    record Acceptable(UtcInstant enrolledAt) implements TotpResetCheck {

        public Acceptable {
            Objects.requireNonNull(enrolledAt, "enrolledAt");
        }
    }

    /**
     * 受け付けない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserAdministrationRefusal<LoginCredentialRejection> refusal) implements TotpResetCheck {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
