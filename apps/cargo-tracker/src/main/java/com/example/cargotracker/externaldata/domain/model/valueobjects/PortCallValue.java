package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 寄港の値。航海の値の寄港の 1 つで、港と到着・出発（UTC）を持つ（Bolt 39b2b）。到着か出発の少なくとも一方を持ち（両方も可）、
 * 出発は到着以後（同じ時刻は受ける）。経路設計の寄港（{@code PortCall}）の規則の写しで、写しを守るテストで一致を確かめる。
 * 港・到着・出発が同じ寄港は同じ値。
 *
 * @param port 港（UN/LOCODE）
 * @param arrivalAt 到着（ないときは null）
 * @param departureAt 出発（ないときは null）
 */
@ValueObject
public record PortCallValue(Location port, UtcInstant arrivalAt, UtcInstant departureAt) {

    public PortCallValue {
        Objects.requireNonNull(port, "port");
        if (arrivalAt == null && departureAt == null) {
            throw new IllegalArgumentException("到着か出発が要ります: " + port.unLocode());
        }
        if (arrivalAt != null && departureAt != null && departureAt.instant().isBefore(arrivalAt.instant())) {
            throw new IllegalArgumentException("到着より前に出発しています: " + port.unLocode());
        }
    }
}
