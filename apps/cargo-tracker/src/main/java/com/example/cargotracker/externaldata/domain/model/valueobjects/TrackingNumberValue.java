package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 追跡番号の値。主要実績の値の追跡番号で、{@code CT} と英大文字・数字 12 桁（0・O・1・I・L を除く）（Bolt 39b2b）。
 * 追跡の追跡番号（{@code TrackingNumber}）の形の写しで、外部データは追跡の型を参照しない（モジュールの規則）。写しを守るテストで一致を
 * 確かめる。
 *
 * @param value 表記
 */
@ValueObject
public record TrackingNumberValue(String value) {

    private static final Pattern FORMAT = Pattern.compile("CT[A-HJKMNP-Z2-9]{12}");

    public TrackingNumberValue {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("追跡番号の形が違います: " + value);
        }
    }
}
