package com.example.cargotracker.quotation.domain.model.rules;

import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;

/**
 * MVP 受付範囲。貨物種別が一般かを判定する（Q-INV-02、BR-03）。
 * 後続段階で特殊貨物を受け付けるときは、この規則を差し替える。
 */
@DomainRule
public class MvpAcceptancePolicy {

    /** その貨物種別の輸送要求を MVP で受け付けるか。 */
    public boolean accepts(CargoCategory category) {
        return category == CargoCategory.GENERAL;
    }
}
