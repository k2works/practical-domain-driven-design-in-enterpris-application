package com.example.cargotracker.routing.interfaces.api;

import java.util.List;

/**
 * 確定した経路版の区間の照会（経路設計の公開 API。ADR-015、units.md の U3 → U2「有効な経路予定と版」。Bolt 25）。追跡が DE-07 を受けて、
 * 予定として採用する区間を引く。経路版は不変なので、確定した区間と同じものが返る。候補の算出の内部過程は公開しない。
 */
public interface RouteVersionLegQuery {

    /**
     * 確定した経路版の区間を区間の順に返す。
     *
     * @param routingCaseNumber 案件番号の表記
     * @param routeVersionNo 経路版番号
     * @return 区間の列。案件・経路版がない、または確定していない経路版なら空
     */
    List<RouteVersionLeg> confirmedLegsOf(String routingCaseNumber, int routeVersionNo);
}
