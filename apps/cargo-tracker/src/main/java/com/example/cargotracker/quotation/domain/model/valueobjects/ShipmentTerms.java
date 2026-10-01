package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import java.util.Objects;

/**
 * 輸送条件。Bolt 1 では出発地と目的地だけを持つ（荷受人・希望到着期限・貨物は後の Bolt で足す）。
 *
 * @param origin 出発地
 * @param destination 目的地
 */
@ValueObject
public record ShipmentTerms(Location origin, Location destination) {

    public ShipmentTerms {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
    }
}
