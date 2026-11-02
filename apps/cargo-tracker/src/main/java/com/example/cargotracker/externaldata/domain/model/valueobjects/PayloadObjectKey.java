package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 原本のオブジェクトキー。保存した原本を指すキーで、前置きでファイル全体（{@code externaldata/ingestions/{UUID}}）と行の payload
 * （{@code externaldata/records/{UUID}}）を区別する（Bolt 39）。呼び手が作る。形を検査し、保存先の外を指すキーや前置きの違うキーを作れない。
 *
 * @param value キーの文字列
 */
@ValueObject
public record PayloadObjectKey(String value) {

    private static final String INGESTIONS = "externaldata/ingestions/";
    private static final String RECORDS = "externaldata/records/";
    private static final Pattern FORM = Pattern.compile(
            "externaldata/(ingestions|records)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    public PayloadObjectKey {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("原本のオブジェクトキーの形が違う: " + value);
        }
    }

    /** 取込のファイル全体を保存する新しいキー。 */
    public static PayloadObjectKey newIngestionFile() {
        return new PayloadObjectKey(INGESTIONS + UUID.randomUUID());
    }

    /** 行の payload を保存する新しいキー。 */
    public static PayloadObjectKey newRecordPayload() {
        return new PayloadObjectKey(RECORDS + UUID.randomUUID());
    }
}
