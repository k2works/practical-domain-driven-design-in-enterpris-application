package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * 予約 ID。システムが発行する不透明な値。画面と URL には追跡番号を出す（D-4 と同じ考え方。Bolt 23）。
 *
 * @param value 識別子
 */
@ValueObject
public record BookingId(UUID value) {

    public BookingId {
        Objects.requireNonNull(value, "value");
    }
}
