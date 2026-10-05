package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.UUID;

/**
 * DE-03 見積りを提示した。見積り（輸送要求を見積提示済みにする）が購読する。KPI 計測（KPI-01 の終了時刻）は US-21、
 * 荷主への通知は US-22 で購読する。業務キーは見積り ID。イベントはドメインの型を持たない（経由地は UN/LOCODE の文字列）。
 *
 * @param quotationId 見積り ID
 * @param quotationNo 見積り番号（輸送要求の中で 1 から）
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 見積りの対象の輸送要求の版番号
 * @param expiresAt 有効期限
 * @param routeVia 経路方針の主な経由地（UN/LOCODE）
 * @param departureAt 経路方針の概算の出発日時
 * @param arrivalAt 経路方針の概算の到着日時
 * @param presentedAt 提示時刻
 */
@DomainEvent
public record QuotationPresented(
        UUID quotationId,
        int quotationNo,
        UUID transportRequestId,
        int transportRequestVersionNo,
        UtcInstant expiresAt,
        List<String> routeVia,
        UtcInstant departureAt,
        UtcInstant arrivalAt,
        UtcInstant presentedAt) {

    public QuotationPresented {
        routeVia = List.copyOf(routeVia);
    }
}
