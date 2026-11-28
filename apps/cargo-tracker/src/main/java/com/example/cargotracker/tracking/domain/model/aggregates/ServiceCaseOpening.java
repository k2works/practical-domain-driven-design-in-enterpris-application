package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import java.util.Objects;
import java.util.Set;

/**
 * 有人案件の起票の結果（Bolt 40-5。SC-INV-01）。必須項目がそろえば起票し、足りなければ案件を作らず不足の項目を返す。
 */
public sealed interface ServiceCaseOpening {

    /**
     * 起票した。
     *
     * @param serviceCase 受付済みの有人案件
     */
    record Opened(ServiceCase serviceCase) implements ServiceCaseOpening {
        public Opened {
            Objects.requireNonNull(serviceCase, "serviceCase");
        }
    }

    /**
     * 必須項目が足りない（案件を作らない。呼び手が入力を保持して不足を示す）。
     *
     * @param missing 不足の項目（1 つ以上）
     */
    record Incomplete(Set<MissingItem> missing) implements ServiceCaseOpening {
        public Incomplete {
            missing = Set.copyOf(missing);
            if (missing.isEmpty()) {
                throw new IllegalArgumentException("不足の項目は 1 つ以上");
            }
        }
    }
}
