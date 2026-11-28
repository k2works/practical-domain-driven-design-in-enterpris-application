package com.example.cargotracker.shared.domain;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;

/**
 * 出典。実績や採用値の根拠。種類・参照・取得時刻を必須とする（ドメインモデルの共有カーネル。Bolt 26b）。
 *
 * @param kind 種類
 * @param reference 参照（原本の受付番号、現場の記録番号など）
 * @param acquiredAt 取得時刻
 */
@ValueObject
public record Source(SourceKind kind, String reference, UtcInstant acquiredAt) {

    /** 参照の上限の文字数（データモデルの {@code source_ref VARCHAR(200)}）。 */
    public static final int REFERENCE_MAX_LENGTH = 200;

    public Source {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        if (reference.isBlank()) {
            throw new IllegalArgumentException("出典の参照がありません");
        }
        if (reference.length() > REFERENCE_MAX_LENGTH) {
            throw new IllegalArgumentException("出典の参照は " + REFERENCE_MAX_LENGTH + " 文字までです: " + reference.length());
        }
    }
}
