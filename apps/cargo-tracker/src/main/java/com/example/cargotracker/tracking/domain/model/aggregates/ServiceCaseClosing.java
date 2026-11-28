package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import java.util.Objects;
import java.util.Set;

/**
 * 有人案件の終結の結果（Bolt 40-5。SC-INV-02）。復旧照合が必要で未完了なら終結せず、不足する照合結果を返す。
 */
public sealed interface ServiceCaseClosing {

    /**
     * 終結した。
     *
     * @param serviceCase 終結した有人案件
     */
    record Closed(ServiceCase serviceCase) implements ServiceCaseClosing {
        public Closed {
            Objects.requireNonNull(serviceCase, "serviceCase");
        }
    }

    /**
     * 復旧照合が未完了なので終結できない（状態は回答済みのまま）。
     *
     * @param missing 不足する照合結果（1 つ以上）
     */
    record ReconciliationIncomplete(Set<MissingItem> missing) implements ServiceCaseClosing {
        public ReconciliationIncomplete {
            missing = Set.copyOf(missing);
            if (missing.isEmpty()) {
                throw new IllegalArgumentException("不足の項目は 1 つ以上");
            }
        }
    }
}
