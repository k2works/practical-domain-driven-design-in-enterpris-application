package com.example.cargotracker.tracking.application.internal.eventhandlers;

import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone.InvalidEventValueException;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.UUID;

/**
 * DE-13 の入力の変換（Bolt 40-1 の M3。W7 週全体レビュー）。届いた文字列から追跡の言葉の値を作り、形が違う・知らない・欠けていれば
 * {@link ExternalMilestone.InvalidEventValueException}（再配信しても直らないので、listener は ERROR のログを残して捨てる）にする。値オブジェクトの
 * 不変条件（必須の値、出典の種類）は {@link ExternalMilestone} 自身が守る。
 */
final class ExternalMilestoneInput {

    private ExternalMilestoneInput() {}

    /** 届いた追跡番号の文字列から追跡番号を作る（形が違えば内容の欠けとして扱う）。 */
    static TrackingNumber parseTrackingNumber(String value) {
        required(value, "trackingNumber");
        try {
            return new TrackingNumber(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventValueException("trackingNumber: " + e.getMessage());
        }
    }

    /** 届いた種類の名前から種類を作る（知らない種類は内容の欠けとして扱う）。 */
    static MilestoneKind parseKind(String name) {
        required(name, "kind");
        try {
            return MilestoneKind.valueOf(name);
        } catch (IllegalArgumentException _) {
            throw new InvalidEventValueException("kind: " + name);
        }
    }

    /** 届いた値から実績の外部の識別を作る（欠けていれば内容の欠けとして扱う）。 */
    static ExternalOrigin externalOrigin(UUID dataSourceId, String externalId, UtcInstant publishedAt) {
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
}
