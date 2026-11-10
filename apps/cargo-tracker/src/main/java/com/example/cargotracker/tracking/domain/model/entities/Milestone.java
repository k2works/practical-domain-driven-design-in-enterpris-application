package com.example.cargotracker.tracking.domain.model.entities;

import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
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
 * @param externalOrigin 実績の外部の識別（出典が外部原本の実績だけが持つ。ほかの出典は {@code null}。Bolt 39g）
 */
@Entity
@SuppressWarnings("java:S107") // 実績の属性をそのまま持つ（保存の形と同じ）
public record Milestone(
        int milestoneNo,
        MilestoneKind kind,
        Location location,
        UtcInstant occurredAt,
        Source source,
        MilestoneState state,
        UserId registeredBy,
        UtcInstant registeredAt,
        ExternalOrigin externalOrigin) {

    public Milestone {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(registeredBy, "registeredBy");
        Objects.requireNonNull(registeredAt, "registeredAt");
        // 表の CHECK（ck_milestone_external_origin）と同じ規則。8 引数のコンストラクタでも作れるので、ここで守る（Bolt 39g）
        if ((source.kind() == SourceKind.EXTERNAL_RECORD) != (externalOrigin != null)) {
            throw new IllegalArgumentException("出典が外部原本の実績だけが外部の識別を持ちます: 出典の種類 " + source.kind());
        }
    }

    /** 外部の識別を持たない実績（出典が外部原本でない実績）を作る。 */
    public Milestone(
            int milestoneNo,
            MilestoneKind kind,
            Location location,
            UtcInstant occurredAt,
            Source source,
            MilestoneState state,
            UserId registeredBy,
            UtcInstant registeredAt) {
        this(milestoneNo, kind, location, occurredAt, source, state, registeredBy, registeredAt, null);
    }
}
