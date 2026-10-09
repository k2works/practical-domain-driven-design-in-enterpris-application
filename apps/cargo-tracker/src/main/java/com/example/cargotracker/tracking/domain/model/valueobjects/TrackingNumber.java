package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 追跡番号。顧客が追跡を照会するための番号で、予約が発行し（BR-07）、追跡は DE-07 の写しとして受け取る。追跡のコンテキスト固有の値で、
 * 予約の型を参照しない（BC の独立。Bolt 25）。形は予約の発行する形（{@code CT} と、紛らわしい文字を除いた英大文字・数字 12 桁）と同じ。
 *
 * @param value 表記
 */
@ValueObject
public record TrackingNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("CT[A-HJKMNP-Z2-9]{12}");

    public TrackingNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("追跡番号の形式ではありません: " + value);
        }
    }
}
