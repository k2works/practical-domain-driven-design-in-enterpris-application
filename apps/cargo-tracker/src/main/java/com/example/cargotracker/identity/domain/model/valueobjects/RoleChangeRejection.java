package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Role;
import java.util.Objects;
import java.util.Set;

/**
 * 役割の変更の拒否。役割の変更を受け付けない理由と、画面に示す役割（企業の種類に合わない役割、職務分離で競合する役割。ほかの理由では空）
 * （Bolt 34。US-16 AC3）。
 *
 * @param reason 理由
 * @param roles 示す役割
 */
@ValueObject
public record RoleChangeRejection(RoleChangeRejectionReason reason, Set<Role> roles) {

    public RoleChangeRejection {
        Objects.requireNonNull(reason, "reason");
        roles = Set.copyOf(roles);
    }

    /** 示す役割のない拒否。 */
    public static RoleChangeRejection of(RoleChangeRejectionReason reason) {
        return new RoleChangeRejection(reason, Set.of());
    }
}
