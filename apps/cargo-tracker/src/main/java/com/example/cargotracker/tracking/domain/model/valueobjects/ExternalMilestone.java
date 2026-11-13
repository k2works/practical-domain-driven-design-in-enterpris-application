package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;

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
