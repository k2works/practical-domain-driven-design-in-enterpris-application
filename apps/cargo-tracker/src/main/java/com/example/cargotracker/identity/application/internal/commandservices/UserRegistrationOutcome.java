package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 利用者の登録の結果（{@link UserRegistrationService#register}。Bolt 38b2）。
 */
public sealed interface UserRegistrationOutcome {

    /**
     * 登録した（利用者・役割・監査記録を書いた）。
     *
     * @param userId 登録した利用者
     */
    record Registered(UserId userId) implements UserRegistrationOutcome {

        public Registered {
            Objects.requireNonNull(userId, "userId");
        }
    }

    /**
     * 受け付けない。利用者は書いていない（管理範囲の外なら、その試行だけを記録した）。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserRegistrationRefusal refusal) implements UserRegistrationOutcome {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
