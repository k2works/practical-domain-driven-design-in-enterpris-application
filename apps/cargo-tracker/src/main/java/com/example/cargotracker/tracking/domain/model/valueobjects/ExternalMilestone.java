package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * 採用した主要実績の値。DE-13 が運ぶ主要実績の採用値を追跡の言葉にした写し（Bolt 39g）。listener が DE-13 から作り、追跡記録が受け取る。追跡は外部データの型を持たない。
 * 必須の値が欠けていれば {@link InvalidEventValueException}（再配信しても直らない、内容の欠けたイベント）。
 *
 * @param trackingNumber 追跡番号
 * @param kind 種類
 * @param location 場所
 * @param occurredAt 発生時刻
 * @param source 出典（種類は外部原本、参照は受信記録 ID、取得時刻）
 * @param origin 実績の外部の識別
 * @param operator 外部データの操作者（データ責任者）
 */
@ValueObject
public record ExternalMilestone(
        TrackingNumber trackingNumber,
        MilestoneKind kind,
        Location location,
        UtcInstant occurredAt,
        Source source,
        ExternalOrigin origin,
        UserId operator) {

    public ExternalMilestone {
        required(trackingNumber, "trackingNumber");
        required(kind, "kind");
        required(location, "location");
        required(occurredAt, "occurredAt");
        required(source, "source");
        required(origin, "origin");
        required(operator, "operator");
        if (source.kind() != SourceKind.EXTERNAL_RECORD) {
            throw new InvalidEventValueException("source.kind: " + source.kind());
        }
    }

    /** 届いた追跡番号の文字列から追跡番号を作る（形が違えば内容の欠けとして扱う）。 */
    public static TrackingNumber parseTrackingNumber(String value) {
        required(value, "trackingNumber");
        try {
            return new TrackingNumber(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventValueException("trackingNumber: " + e.getMessage());
        }
    }

    /** 届いた種類の名前から種類を作る（知らない種類は内容の欠けとして扱う）。 */
    public static MilestoneKind parseKind(String name) {
        required(name, "kind");
        try {
            return MilestoneKind.valueOf(name);
        } catch (IllegalArgumentException _) {
            throw new InvalidEventValueException("kind: " + name);
        }
    }

    /** 届いた値から実績の外部の識別を作る（欠けていれば内容の欠けとして扱う）。 */
    public static ExternalOrigin externalOrigin(UUID dataSourceId, String externalId, UtcInstant publishedAt) {
        required(dataSourceId, "dataSourceId");
        required(publishedAt, "publishedAt");
        if (externalId == null || externalId.isBlank()) {
            throw new InvalidEventValueException("externalId");
        }
        return new ExternalOrigin(dataSourceId, externalId, publishedAt);
    }

    private static void required(Object value, String name) {
        if (value == null) {
            throw new InvalidEventValueException(name);
        }
    }

    /** 届いたイベントの値が欠けている、または規則に合わない（内容の欠けたイベント。listener はこの型だけを捨てる）。 */
    public static final class InvalidEventValueException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        /** @param detail 欠けている値の名前と理由（ログに出す） */
        public InvalidEventValueException(String detail) {
            super("主要実績の採用値の内容が欠けている: " + detail);
        }
    }
}
