package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 主要実績の値。主要実績のファイルの行から読んだ追跡番号の値・実績の種類の値・場所・発生時刻（Bolt 39b2b）。
 *
 * @param trackingNumber 追跡番号の値
 * @param kind 実績の種類の値
 * @param location 場所（UN/LOCODE）
 * @param occurredAt 発生時刻（UTC。マイクロ秒に切り捨てた値）
 */
@ValueObject
public record MilestoneValues(
        TrackingNumberValue trackingNumber, MilestoneKindValue kind, Location location, UtcInstant occurredAt)
        implements RecordValues {

    public MilestoneValues {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** 要約は「種類;場所;発生時刻」（例「DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z」。Bolt 39b3）。 */
    @Override
    public String summary() {
        return kind.name() + ";" + location.unLocode() + ";" + RecordValues.summaryTime(occurredAt);
    }
}
