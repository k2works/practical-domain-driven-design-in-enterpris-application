package com.example.cargotracker.quotation.api;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 経路の割当ての依頼（見積りの公開 API。Bolt 20）。業務キーは見積り ID と経路版（案件番号・経路版番号）。
 *
 * @param quotationId 依頼元の見積り ID
 * @param routingCaseNumber 案件番号の表記（例: RC-2026-0001）
 * @param routeVersionNo 確定した経路版番号
 * @param confirmedAt 経路を確定した時刻（承認 commit 時刻）
 * @param legs 確定した候補の区間（区間の順。1 件以上）
 */
public record RouteAssignmentRequest(
        UUID quotationId,
        String routingCaseNumber,
        int routeVersionNo,
        UtcInstant confirmedAt,
        List<RouteAssignmentLeg> legs) {

    public RouteAssignmentRequest {
        Objects.requireNonNull(quotationId, "quotationId");
        Objects.requireNonNull(routingCaseNumber, "routingCaseNumber");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        legs = List.copyOf(legs);
    }
}
