package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * 経路設計案件 ID。システムが発行する不透明な値。画面と URL には案件番号を出す（D-4 と同じ考え方。Bolt 17）。
 *
 * @param value 識別子
 */
@ValueObject
public record RoutingCaseId(UUID value) {

    public RoutingCaseId {
        Objects.requireNonNull(value, "value");
    }
}
