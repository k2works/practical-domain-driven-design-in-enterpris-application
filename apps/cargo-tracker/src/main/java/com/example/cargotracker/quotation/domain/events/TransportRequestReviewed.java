package com.example.cargotracker.quotation.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-02 輸送要求を審査した。監査（US-17）が購読する。それまでは発行だけ。
 * 業務キーは輸送要求 ID・版番号・判断。イベントはドメインの型を持たないため、判断は文字列で持つ。
 *
 * @param transportRequestId 輸送要求 ID
 * @param versionNo 審査した版番号
 * @param decision 判断（{@code APPROVED}・{@code SENT_BACK}）
 * @param reviewerId 判断者
 * @param decidedAt 判断時刻
 */
@DomainEvent
public record TransportRequestReviewed(
        UUID transportRequestId, int versionNo, String decision, UserId reviewerId, UtcInstant decidedAt) {}
