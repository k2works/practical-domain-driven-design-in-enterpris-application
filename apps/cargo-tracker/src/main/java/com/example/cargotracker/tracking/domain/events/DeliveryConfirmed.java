package com.example.cargotracker.tracking.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-10 引渡しを確認した（ADR-014。Bolt 39g2）。登録・採用で状態が採用になった引渡しの実績ごとに、追跡記録の保存と同じトランザクションで発行する
 * （追跡が確認中でも出す。【業務の判断】(al)）。追跡の listener が受けて、予約の公開 API で予約を完了にする。追跡記録の予約状態の写しは完了にしない
 * （【前の決定の変更】(17)）。イベントはドメインの型を持たない。
 *
 * @param trackingNumber 追跡番号の表記
 * @param bookingId 予約 ID
 * @param milestoneNo 採用になった引渡しの実績番号
 * @param occurredAt 引渡しの発生時刻
 * @param operator 登録者か採用者の利用者 ID
 * @param aggregateVersion 発行元の集約の版（保存した後の版。B-INV-12）
 */
@DomainEvent
public record DeliveryConfirmed(
        String trackingNumber,
        UUID bookingId,
        int milestoneNo,
        UtcInstant occurredAt,
        UUID operator,
        long aggregateVersion) {}
