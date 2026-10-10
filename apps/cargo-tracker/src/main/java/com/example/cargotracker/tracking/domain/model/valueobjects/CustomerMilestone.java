package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 荷主向けの主要実績。荷主に見せてよい項目（種類・場所・発生時刻・出典）だけを持つ主要実績の写し（BR-07、T-INV-09。Bolt 27）。
 * 実績番号・実績の状態・登録者・登録時刻は社内の情報なので持たない。
 *
 * @param kind 種類
 * @param location 場所
 * @param occurredAt 発生時刻
 * @param source 出典（種類・参照・取得時刻）
 */
@ValueObject
public record CustomerMilestone(MilestoneKind kind, Location location, UtcInstant occurredAt, Source source) {

    public CustomerMilestone {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(source, "source");
    }
}
