package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 経路設計案件一覧（S-05）の 1 行（Bolt 17）。
 *
 * @param number 案件番号
 * @param transportRequestNumber 業務番号の表記
 * @param transportRequestVersionNo 輸送要求の版番号
 * @param origin 出発地
 * @param destination 目的地
 * @param arrivalDeadline 希望到着期限
 * @param requestedAt 詳細経路設計の依頼時刻
 * @param status 経路版の状態
 */
public record RoutingCaseSummary(
        RoutingCaseNumber number,
        String transportRequestNumber,
        int transportRequestVersionNo,
        Location origin,
        Location destination,
        UtcInstant arrivalDeadline,
        UtcInstant requestedAt,
        RouteVersionStatus status) {

    public RoutingCaseSummary {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(arrivalDeadline, "arrivalDeadline");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(status, "status");
    }
}
