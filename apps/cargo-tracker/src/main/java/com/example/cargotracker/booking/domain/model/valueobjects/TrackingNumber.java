package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * 追跡番号。顧客が追跡を照会するための推測されにくい番号（BR-07）。{@code CT} と、紛らわしい文字（0・O・1・I・L）を除いた
 * 英大文字・数字 12 桁の 14 文字（約 59 bit。Bolt 23）。業務番号や連番から推測できない。
 *
 * @param value 表記
 */
@ValueObject
public record TrackingNumber(String value) {

    private static final String PREFIX = "CT";
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_LENGTH = 12;
    private static final Pattern FORMAT = Pattern.compile("CT[A-HJKMNP-Z2-9]{12}");

    public TrackingNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("追跡番号の形式ではありません: " + value);
        }
    }

    /**
     * 乱数から追跡番号を作る。一意かどうかは発行する側（{@code TrackingNumberIssuer}）が確かめる。
     *
     * @param random 乱数（本番は暗号論的に安全な乱数）
     * @return 追跡番号
     */
    public static TrackingNumber generate(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        StringBuilder value = new StringBuilder(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new TrackingNumber(value.toString());
    }
}
