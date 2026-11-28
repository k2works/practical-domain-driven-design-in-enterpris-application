package com.example.cargotracker.routing.interfaces.api;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 確定した経路版の区間（経路設計の公開 API。ADR-015。Bolt 25）。航海の採用情報版と取得時刻（経路設計の判定の根拠）は含めない。
 *
 * @param voyageNumber 航海番号
 * @param load 積地
 * @param discharge 揚地
 * @param departureAt 出発予定（積地）
 * @param arrivalAt 到着予定（揚地）
 */
public record RouteVersionLeg(
        String voyageNumber, Location load, Location discharge, UtcInstant departureAt, UtcInstant arrivalAt) {

    public RouteVersionLeg {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(discharge, "discharge");
        Objects.requireNonNull(departureAt, "departureAt");
        Objects.requireNonNull(arrivalAt, "arrivalAt");
    }
}
