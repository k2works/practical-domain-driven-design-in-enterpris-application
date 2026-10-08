package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-04 見積りを荷主が承認した（US-24 AC4、Q-INV-10。Bolt 20）。見積り（輸送要求を予約待ちにする）が購読する。
 * 監査（US-17）と営業の業務ホーム（予約の確定待ち。US-04）は後の Bolt で購読する。業務キーは見積り ID。
 * イベントはドメインの型を持たない。
 *
 * @param quotationId 見積り ID
 * @param quotationNo 見積り番号
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 見積りの対象の輸送要求の版番号
 * @param routingCaseNumber 承認した経路版の案件番号の表記
 * @param routeVersionNo 承認した経路版番号
 * @param approvedBy 承認した荷主担当者の利用者 ID
 * @param approvedAt 承認時刻
 */
@DomainEvent
public record QuotationApprovedByShipper(
        UUID quotationId,
        int quotationNo,
        UUID transportRequestId,
        int transportRequestVersionNo,
        String routingCaseNumber,
        int routeVersionNo,
        UUID approvedBy,
        UtcInstant approvedAt) {}
