package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.rules.IdentityVerificationPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.LoginCredentialRejection;
import java.util.Objects;

/**
 * 管理者による TOTP の解除の結果（{@link UserLoginCredentialService#resetTotp}。Bolt 38c）。画面は解除したなら詳細へ戻し、本人確認の日時
 * の誤りは確認の画面に示し、受け付けなければ拒否の種類で行き先と文言を選ぶ。
 */
public sealed interface TotpResetOutcome {

    /** 資格と回復コードを消し、監査記録を書き、その利用者の session を消した（消せなくても解除は戻さない）。 */
    record Reset() implements TotpResetOutcome {}

    /**
     * 本人確認の日時が規則の範囲の外。何も書いていない。
     *
     * @param violation 範囲の外の理由
     */
    record Unverifiable(IdentityVerificationPolicy.Violation violation) implements TotpResetOutcome {

        public Unverifiable {
            Objects.requireNonNull(violation, "violation");
        }
    }

    /**
     * 受け付けない。何も書いていない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserAdministrationRefusal<LoginCredentialRejection> refusal) implements TotpResetOutcome {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
