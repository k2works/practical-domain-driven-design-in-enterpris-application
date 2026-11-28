package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.UUID;

/**
 * DE-16 荷主が詳細経路設計を依頼した（US-24 AC1。Bolt 12）。見積り（輸送要求を経路設計中にする）が購読する。経路設計
 * （経路設計案件を作る。R-INV-10）は US-06 で購読する。経路条件（出発地・目的地・希望到着期限・貨物）は持たず、経路設計が
 * 見積りの公開 API から問い合わせる（形は US-06 で決める）。業務キーは見積り ID。イベントはドメインの型を持たない。
 *
 * @param quotationId 見積り ID
 * @param quotationNo 見積り番号（輸送要求の中で 1 から）
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 見積りの対象の輸送要求の版番号
 * @param routeVia 経路方針の主な経由地（UN/LOCODE）
 * @param departureAt 経路方針の概算の出発日時
 * @param arrivalAt 経路方針の概算の到着日時
 * @param expiresAt 見積りの有効期限
 * @param requestedBy 依頼者（回答した荷主担当者）の利用者 ID
 * @param requestedAt 依頼時刻（回答時刻）
 */
@DomainEvent
public record RouteDesignRequested(
        UUID quotationId,
        int quotationNo,
        UUID transportRequestId,
        int transportRequestVersionNo,
        List<String> routeVia,
        UtcInstant departureAt,
        UtcInstant arrivalAt,
        UtcInstant expiresAt,
        UUID requestedBy,
        UtcInstant requestedAt) {

    public RouteDesignRequested {
        routeVia = List.copyOf(routeVia);
    }
}
