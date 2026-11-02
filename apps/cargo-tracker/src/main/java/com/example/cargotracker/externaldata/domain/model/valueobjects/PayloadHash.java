package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 原本のハッシュ。原本のバイト列の SHA-256 の 16 進 64 文字（小文字）。保存の前に呼び手が計算し、重複と隔離の判定に使う
 * （data_model の {@code payload_hash}・{@code file_hash}。Bolt 39）。
 *
 * @param value 16 進 64 文字
 */
@ValueObject
public record PayloadHash(String value) {

    private static final Pattern FORM = Pattern.compile("[0-9a-f]{64}");

    public PayloadHash {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("原本のハッシュは SHA-256 の小文字の 16 進 64 文字: " + value);
        }
    }

    /** バイト列のハッシュを求める。 */
    public static PayloadHash of(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            return new PayloadHash(HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 は Java の実行環境が必ず持つ", e);
        }
    }
}
