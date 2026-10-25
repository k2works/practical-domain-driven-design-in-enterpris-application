package com.example.cargotracker.identity.application.internal.commandservices;

import java.util.Objects;

/**
 * 利用者の登録を受け付けられるかを確かめた結果（{@link UserRegistrationService#check}。Bolt 38b2）。何も書いていない。
 */
public sealed interface UserRegistrationCheck {

    /** この登録を受け付けられる。 */
    record Acceptable() implements UserRegistrationCheck {}

    /**
     * 受け付けない。
     *
     * @param refusal 受け付けない理由
     */
    record Refused(UserRegistrationRefusal refusal) implements UserRegistrationCheck {

        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
