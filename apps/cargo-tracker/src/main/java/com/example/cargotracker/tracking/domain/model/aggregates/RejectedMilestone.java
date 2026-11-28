package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.Objects;
import java.util.UUID;

/**
 * 拒否した主要実績。追跡が実績にしなかった DE-13 の記録（Bolt 39g の決定 (3)・(6)・(7)）。追記専用で、同じ出典（種類と参照）は 1 件だけ。W8 の有人例外案件がこの記録を受ける。
 * 知らない追跡番号も写すので、追跡記録への参照を持たない。
 *
 * @param id 拒否の記録 ID
 * @param trackingNumber 追跡番号
 * @param kind 種類
 * @param location 場所
 * @param occurredAt 発生時刻
 * @param source 出典
 * @param dataSourceId 情報源 ID
 * @param externalId 外部 ID
 * @param reason 拒否の理由
 * @param recordedAt 記録した時刻
 */
@AggregateRoot
@SuppressWarnings("java:S107") // 記録する値をそのまま持つ（保存の形と同じ）
public record RejectedMilestone(
        UUID id,
        TrackingNumber trackingNumber,
        MilestoneKind kind,
        Location location,
        UtcInstant occurredAt,
        Source source,
        UUID dataSourceId,
        String externalId,
        MilestoneRejectionReason reason,
        UtcInstant recordedAt) {

    public RejectedMilestone {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }

    /** 実績にしなかった DE-13 の値から記録を作る。 */
    public static RejectedMilestone of(
            UUID id, ExternalMilestone external, MilestoneRejectionReason reason, UtcInstant recordedAt) {
        return new RejectedMilestone(
                id,
                external.trackingNumber(),
                external.kind(),
                external.location(),
                external.occurredAt(),
                external.source(),
                external.origin().dataSourceId(),
                external.origin().externalId(),
                reason,
                recordedAt);
    }
}
