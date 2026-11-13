package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;

/**
 * 受付番号。有人案件の一意な識別子（Bolt 40-5。表の {@code case_number VARCHAR(20)}）。採番は永続化の側（Bolt 40-6）が行い、ここでは形だけを守る。
 *
 * @param value 受付番号（空でなく 20 文字まで）
 */
@ValueObject
public record CaseNumber(String value) {

    /** 受付番号の長さの上限（表の列と同じ）。 */
    public static final int MAX_LENGTH = 20;

    public CaseNumber {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("受付番号は空でなく " + MAX_LENGTH + " 文字まで: " + value);
        }
    }

    /** 年と連番から受付番号を作る（{@code SC-YYYY-NNNN}。連番は 4 桁に満たなければ 0 で埋める）。 */
    public static CaseNumber of(int year, int serial) {
        if (year < 1 || serial < 1) {
            throw new IllegalArgumentException("年と連番は 1 以上: " + year + ", " + serial);
        }
        return new CaseNumber(String.format("SC-%04d-%04d", year, serial));
    }
}
