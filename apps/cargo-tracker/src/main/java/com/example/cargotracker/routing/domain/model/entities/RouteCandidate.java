package com.example.cargotracker.routing.domain.model.entities;

import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Comparator;
import java.util.List;
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
}
