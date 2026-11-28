package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * 見積り ID。システムが発行する不透明な値。画面と URL には見積り番号を出す（D-4 と同じ考え方。Bolt 10）。
 *
 * @param value 識別子
 */
@ValueObject
public record QuotationId(UUID value) {

    public QuotationId {
        Objects.requireNonNull(value, "value");
    }
}
