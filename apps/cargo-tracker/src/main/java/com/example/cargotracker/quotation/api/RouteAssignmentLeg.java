package com.example.cargotracker.quotation.api;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 経路の割当ての区間（見積りの公開 API。Bolt 20）。
 *
 * @param voyageNumber 航海番号
 * @param load 積地
 * @param discharge 揚地
 * @param departureAt 出発予定
 * @param arrivalAt 到着予定
 */
public record RouteAssignmentLeg(
        String voyageNumber, Location load, Location discharge, UtcInstant departureAt, UtcInstant arrivalAt) {

    public RouteAssignmentLeg {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(discharge, "discharge");
        Objects.requireNonNull(departureAt, "departureAt");
        Objects.requireNonNull(arrivalAt, "arrivalAt");
    }
}
