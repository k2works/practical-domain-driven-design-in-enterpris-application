package com.example.cargotracker.routing.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.UUID;

/**
 * DE-05 経路を確定した（US-07 AC1。Bolt 19）。見積り（依頼元の見積りに経路版を割り当て、荷主承認待ちにする。R-INV-11）は
 * US-24 AC4 で購読する。業務キーは経路設計案件 ID と経路版番号。イベントはドメインの型を持たない。
 *
 * @param routingCaseId 経路設計案件 ID
 * @param caseNumber 案件番号（表示用の文字列）
 * @param routeVersionNo 確定した経路版番号
 * @param quotationId 依頼元の見積り ID
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 輸送要求の版番号
 * @param approvedBy 承認者（経路設計者）の利用者 ID
 * @param approvedAt 承認 commit 時刻
 * @param referencedInfoVersions 参照情報版（確定した候補の区間の航海の採用情報版。区間の順）
 */
@DomainEvent
public record RouteConfirmed(
        UUID routingCaseId,
        String caseNumber,
        int routeVersionNo,
        UUID quotationId,
        UUID transportRequestId,
        int transportRequestVersionNo,
        UUID approvedBy,
        UtcInstant approvedAt,
        List<String> referencedInfoVersions) {

    public RouteConfirmed {
        referencedInfoVersions = List.copyOf(referencedInfoVersions);
    }
}
