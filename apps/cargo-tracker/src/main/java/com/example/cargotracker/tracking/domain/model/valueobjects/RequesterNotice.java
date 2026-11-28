package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 依頼元への表示。有人案件の担当が決まっているか、決まっていなければ次回更新日時を示す（US-20 AC3。Bolt 40-9）。
 *
 * @param assigneePending 担当未確定か（受領する前、または受領期限を超過して上位対応中）
 * @param nextUpdateAt 次回更新日時
 */
@ValueObject
public record RequesterNotice(boolean assigneePending, UtcInstant nextUpdateAt) {

    public RequesterNotice {
        Objects.requireNonNull(nextUpdateAt, "nextUpdateAt");
    }
}
