package com.example.cargotracker.shared.domain;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * 利用者 ID。操作した利用者を識別する。
 *
 * @param value 識別子
 */
@ValueObject
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }
}
