package com.example.cargotracker.shared.domain;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * 企業 ID。荷主・荷受人・A 社などの企業を識別する。
 *
 * @param value 識別子
 */
@ValueObject
public record CompanyId(UUID value) {

    public CompanyId {
        Objects.requireNonNull(value, "value");
    }
}
