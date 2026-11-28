package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;

/**
 * 冪等性キー。提供元、原本種別、外部 ID、外部版の組で、外部原本を 1 つに定める（BR-12。data_model の
 * {@code uk_external_record_idempotency_key}。Bolt 39b）。4 つの値の同一性で判定する。
 *
 * @param provider 提供元
 * @param recordType 原本種別（取込ファイルの種別の名前。{@code VOYAGE}・{@code MILESTONE}）
 * @param externalId 外部 ID
 * @param externalVersion 外部版
 */
@ValueObject
public record IdempotencyKey(String provider, String recordType, String externalId, String externalVersion) {

    public IdempotencyKey {
        requireText(provider, "provider");
        requireText(recordType, "recordType");
        requireText(externalId, "externalId");
        requireText(externalVersion, "externalVersion");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException("冪等性キーの " + name + " が空");
        }
    }
}
