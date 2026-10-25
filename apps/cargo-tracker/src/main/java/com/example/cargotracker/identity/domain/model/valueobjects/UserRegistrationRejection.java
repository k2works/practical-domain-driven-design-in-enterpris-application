package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Role;
import java.util.Objects;
import java.util.Set;

/**
 * 利用者の登録の拒否。利用者の登録を受け付けない理由と、画面に示す役割（監督の役割、企業の種類に合わない役割、職務分離で競合する役割。
 * 役割なしでは空）（Bolt 38b。US-16 AC1・AC3）。
 *
 * @param reason 理由
 * @param roles 示す役割
 */
@ValueObject
public record UserRegistrationRejection(UserRegistrationRejectionReason reason, Set<Role> roles) {

    public UserRegistrationRejection {
        Objects.requireNonNull(reason, "reason");
        roles = Set.copyOf(roles);
    }
}
