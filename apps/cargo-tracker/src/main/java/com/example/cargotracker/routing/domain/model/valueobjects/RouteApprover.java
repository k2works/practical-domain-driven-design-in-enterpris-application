package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * 経路の承認者。経路を確定した利用者と、確定の時点で経路設計者の役割を持つか（R-INV-04、BR-15。Bolt 19）。
 *
 * @param userId 利用者 ID
 * @param routeDesigner 経路設計者の役割を持つか
 */
@ValueObject
public record RouteApprover(UUID userId, boolean routeDesigner) {

    public RouteApprover {
        Objects.requireNonNull(userId, "userId");
    }
}
