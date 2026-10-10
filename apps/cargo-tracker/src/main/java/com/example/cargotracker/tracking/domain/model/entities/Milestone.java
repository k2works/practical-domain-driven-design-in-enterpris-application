package com.example.cargotracker.tracking.domain.model.entities;

import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import java.util.Objects;

/**
 * 主要実績。出典と発生時刻を伴う、集荷・搬入・出発・積替・到着・引渡しの事実。追跡記録の中で実績番号で識別する（Bolt 26b）。
 * 削除・上書きしない（T-INV-01）。
 *
 * @param milestoneNo 実績番号（追跡記録の中で 1 から）
 * @param kind 種類
 * @param location 場所
 * @param occurredAt 発生時刻
 * @param source 出典
 * @param state 状態
 * @param registeredBy 登録者
 * @param registeredAt 登録時刻
 */
@Entity
public record Milestone(
        int milestoneNo,
        MilestoneKind kind,
        Location location,
        UtcInstant occurredAt,
        Source source,
        MilestoneState state,
        UserId registeredBy,
        UtcInstant registeredAt) {

    public Milestone {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(registeredBy, "registeredBy");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }
}
