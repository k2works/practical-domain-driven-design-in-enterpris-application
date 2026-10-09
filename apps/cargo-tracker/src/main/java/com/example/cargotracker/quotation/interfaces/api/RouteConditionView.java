package com.example.cargotracker.quotation.interfaces.api;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 経路条件の照会の結果（見積りの公開 API。Bolt 17）。輸送要求版の輸送条件のうち、経路設計が使う項目の写し。
 *
 * @param transportRequestNumber 業務番号の表記（例: TR-2026-0001）
 * @param origin 出発地
 * @param destination 目的地
 * @param arrivalDeadline 希望到着期限
 * @param cargoCategory 貨物種別の名前（GENERAL、DANGEROUS、REEFER、OTHER_SPECIAL）
 */
public record RouteConditionView(
        String transportRequestNumber,
        Location origin,
        Location destination,
        UtcInstant arrivalDeadline,
        String cargoCategory) {

    public RouteConditionView {
        Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(arrivalDeadline, "arrivalDeadline");
        Objects.requireNonNull(cargoCategory, "cargoCategory");
    }
}
