package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * 輸送要求 ID。システムが発行する不透明な値。
 *
 * @param value 識別子
 */
@ValueObject
public record TransportRequestId(UUID value) {

    public TransportRequestId {
        Objects.requireNonNull(value, "value");
    }
}
