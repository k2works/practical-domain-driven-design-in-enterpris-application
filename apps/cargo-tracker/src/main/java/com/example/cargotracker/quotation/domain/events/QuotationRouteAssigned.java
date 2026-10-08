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
 */
@DomainEvent
public record QuotationRouteAssigned(
        UUID quotationId,
        int quotationNo,
        UUID transportRequestId,
        int transportRequestVersionNo,
        String routingCaseNumber,
        int routeVersionNo,
        UtcInstant assignedAt) {}
