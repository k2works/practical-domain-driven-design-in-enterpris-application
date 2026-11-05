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

    /** 要約の項目の区切り。 */
    private static final String FIELD_SEPARATOR = ";";

    /** 要約の項目の数（種類・場所・発生時刻）。 */
    private static final int FIELDS = 3;

    public MilestoneValues {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** 要約は「種類;場所;発生時刻」（例「DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z」。Bolt 39b3）。 */
    @Override
    public String summary() {
        return kind.name()
                + FIELD_SEPARATOR
                + location.unLocode()
                + FIELD_SEPARATOR
                + RecordValues.summaryTime(occurredAt);
    }

    /** 対象のキーは追跡番号の値（採用値の対象のキーと同じ。Bolt 39d3）。 */
    @Override
    public String targetKey() {
        return trackingNumber.value();
    }

    /**
     * 要約から主要実績の値に戻す（{@link #summary()} の逆。Bolt 39d3c の開始準備の決定 (2)）。要約を「;」で「種類;場所;発生時刻」の 3 つに分ける。
     * 取込の値 {@code v} について {@code ofSummary(v.targetKey(), v.summary())} は {@code v} と等しい（時刻はマイクロ秒に切り捨てた値に限る）。
     *
     * @param trackingNumber 追跡番号（受信記録・採用値の対象のキー）
     * @param summary 値の要約
     * @return 主要実績の値
     * @throws IllegalArgumentException 要約が規則に合わないとき（区切りの数の違い、知らない種類、UN/LOCODE でない場所、読めない・空の発生時刻、
     *     形の違う追跡番号。黙って読まない）
     */
    public static MilestoneValues ofSummary(String trackingNumber, String summary) {
        Objects.requireNonNull(summary, "summary");
        String[] fields = summary.split(FIELD_SEPARATOR, -1);
        if (fields.length != FIELDS) {
            throw new IllegalArgumentException("主要実績の要約は「種類;場所;発生時刻」の 3 つに区切ります");
        }
        UtcInstant occurredAt = RecordValues.summaryTimeOf(fields[2]);
        if (occurredAt == null) {
            // 発生時刻は必須（空を NullPointerException にせず、ほかの要約の誤りと同じ例外にする）
            throw new IllegalArgumentException("主要実績の要約の発生時刻が空です");
        }
        return new MilestoneValues(
                new TrackingNumberValue(trackingNumber),
                MilestoneKindValue.valueOf(fields[0]),
                new Location(fields[1]),
                occurredAt);
    }
}
