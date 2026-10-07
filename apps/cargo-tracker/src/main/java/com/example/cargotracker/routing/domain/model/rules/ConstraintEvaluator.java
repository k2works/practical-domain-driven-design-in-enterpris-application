package com.example.cargotracker.routing.domain.model.rules;

import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;

/**
 * 制約適合判定。経路条件、区間の列、接続時間規則、判定時刻から、適合・除外と理由を返す純粋な関数（R-INV-01・02、BR-11）。
 * Golden dataset（TST-02、W6）で検証する。
 */
@DomainRule
public class ConstraintEvaluator {

    /** 判定する。骨組み（ステップ 2 の Red）。 */
    public ConstraintEvaluation evaluate(
            RouteSpecification specification, List<Leg> legs, List<ConnectionRule> rules, UtcInstant judgedAt) {
        return new ConstraintEvaluation(legs.getLast().arrivalAt(), List.of(), null);
    }
}
