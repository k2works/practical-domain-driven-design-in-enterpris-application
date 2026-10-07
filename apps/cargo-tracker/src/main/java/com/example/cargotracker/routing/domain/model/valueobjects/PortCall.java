package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 寄港。航海が寄る港と、その到着予定・出発予定。始港は到着予定を、終港は出発予定を持たない。
 *
 * @param port 港
 * @param arrivalAt 到着予定（始港は null）
 * @param departureAt 出発予定（終港は null）
 */
@ValueObject
public record PortCall(Location port, UtcInstant arrivalAt, UtcInstant departureAt) {

    public PortCall {
        Objects.requireNonNull(port, "port");
        if (arrivalAt == null && departureAt == null) {
            throw new IllegalArgumentException("寄港には到着予定か出発予定が要ります: " + port.unLocode());
        }
        if (arrivalAt != null && departureAt != null && departureAt.instant().isBefore(arrivalAt.instant())) {
            throw new IllegalArgumentException("出発予定が到着予定より前です: " + port.unLocode());
        }
    }
}
