package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 経路条件。経路設計案件の出発地・目的地・希望到着期限・貨物種別。見積りの公開 API（経路条件の照会）から得た輸送要求版の写し（Bolt 17）。
 *
 * @param origin 出発地
 * @param destination 目的地
 * @param arrivalDeadline 希望到着期限
 * @param cargoCategory 貨物種別（見積りの貨物種別の名前。R0.1 は一般貨物だけで、判定には使わない）
 */
@ValueObject
public record RouteSpecification(
        Location origin, Location destination, UtcInstant arrivalDeadline, String cargoCategory) {

    public RouteSpecification {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(arrivalDeadline, "arrivalDeadline");
        Objects.requireNonNull(cargoCategory, "cargoCategory");
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("出発地と目的地が同じです: " + origin.unLocode());
        }
    }
}
