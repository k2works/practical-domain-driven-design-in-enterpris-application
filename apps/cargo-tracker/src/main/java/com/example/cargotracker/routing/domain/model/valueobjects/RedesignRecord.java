package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;

/**
 * 再設計要の記録。経路版を再設計要にした時刻と、不適合の理由の区分（1 つ以上。US-07 AC4、R-INV-07。Bolt 37d）。理由の詳細（不適合の
 * 時刻・閾値・参照情報版）は持たない。旧情報版は確定の記録の候補の区間に残る。
 *
 * @param requiredAt 再設計要にした時刻
 * @param reasons 理由の区分（1 つ以上。並びを保つ）
 */
@ValueObject
public record RedesignRecord(UtcInstant requiredAt, List<RedesignReasonCode> reasons) {

    public RedesignRecord {
        Objects.requireNonNull(requiredAt, "requiredAt");
        reasons = List.copyOf(reasons);
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("再設計要の記録には理由が要ります");
        }
    }
}
