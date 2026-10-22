package com.example.cargotracker.routing.domain.model.entities;

import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 経路候補。区間の列からなる候補と、その制約適合判定。経路版の中で候補番号で識別する。
 *
 * @param candidateNo 候補番号（経路版の中で 1 から。適合を先に、到着予定の早い順）
 * @param legs 区間の列
 * @param evaluation 制約適合判定
 * @param evaluatedAt 判定時刻
 */
@Entity
public record RouteCandidate(int candidateNo, List<Leg> legs, ConstraintEvaluation evaluation, UtcInstant evaluatedAt) {

    public RouteCandidate {
        if (candidateNo < 1) {
            throw new IllegalArgumentException("候補番号は 1 以上です: " + candidateNo);
        }
        legs = List.copyOf(legs);
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("候補には区間が要ります");
        }
        Objects.requireNonNull(evaluation, "evaluation");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    /** 情報鮮度。候補に使った航海の情報の取得時刻のうち最も古いもの。 */
    public UtcInstant oldestInfoAcquiredAt() {
        return legs.stream()
                .map(Leg::infoAcquiredAt)
                .min(Comparator.comparing(UtcInstant::instant))
                .orElseThrow();
    }

    /**
     * 情報版の違う区間（US-07 AC3、R-INV-03。Bolt 37c）。区間の採用情報版が、同じ航海番号の今の採用情報版と違うか、今の航海が
     * ない区間を区間の順に返す。確定の照合と、確定した経路版の評価（Bolt 37d）で使う。
     *
     * @param currentInfoVersions 航海番号から今の航海の採用情報版（今の航海の全件。ない航海番号は航海がなくなったとみなす。更新された
     *     航海だけを渡すと、ほかの区間も版が違うと判定する）
     */
    public List<Leg> legsWithChangedInfo(Map<String, String> currentInfoVersions) {
        return legs.stream()
                .filter(leg -> !leg.infoVersion().equals(currentInfoVersions.get(leg.voyageNumber())))
                .toList();
    }
}
