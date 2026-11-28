package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-21 経路版を見積りに割り当てた（US-24 AC4、R-INV-11。Bolt 20）。見積り（輸送要求を荷主承認待ちにする）が購読する。
 * 業務キーは見積り ID と経路版（案件番号・経路版番号）。イベントはドメインの型を持たない。
 *
 * @param quotationId 見積り ID
 * @param quotationNo 見積り番号
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 見積りの対象の輸送要求の版番号
 * @param routingCaseNumber 案件番号の表記
 * @param routeVersionNo 経路版番号
 * @param assignedAt 割当て時刻
 * @param shipperCompanyId 荷主企業 ID（荷主への通知の宛先を identity が引くため。W9 の Bolt 43-1 から。それ以前に発行したものは null）
 * @param transportRequestNumber 業務番号の表記（通知の対象と導線。同上）
 */
@DomainEvent
public record QuotationRouteAssigned(
        UUID quotationId,
        int quotationNo,
        UUID transportRequestId,
        int transportRequestVersionNo,
        String routingCaseNumber,
        int routeVersionNo,
        UtcInstant assignedAt,
        UUID shipperCompanyId,
        String transportRequestNumber) {

    /** 荷主企業と業務番号を持たない形（それ以前に発行したものと、通知を使わない呼び出し）。 */
    public QuotationRouteAssigned(
            UUID quotationId,
            int quotationNo,
            UUID transportRequestId,
            int transportRequestVersionNo,
            String routingCaseNumber,
            int routeVersionNo,
            UtcInstant assignedAt) {
        this(
                quotationId,
                quotationNo,
                transportRequestId,
                transportRequestVersionNo,
                routingCaseNumber,
                routeVersionNo,
                assignedAt,
                null,
                null);
    }
}
