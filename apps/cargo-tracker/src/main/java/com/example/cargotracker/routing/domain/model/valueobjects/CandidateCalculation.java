package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 候補の算出の結果の件数（US-06 AC1。Bolt 17）。見つけた候補が上限を超えたら、残した数と見つけた数が違う。
 *
 * @param found 見つけた候補の数
 * @param kept 残した候補の数（上限 20）
 * @param conforming 残した候補のうち適合の数
 * @param evaluatedAt 判定時刻
 */
public record CandidateCalculation(int found, int kept, int conforming, UtcInstant evaluatedAt) {

    public CandidateCalculation {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    /** 上限で切って示さなかった候補の数。 */
    public int omitted() {
        return found - kept;
    }

    /** 残した候補のうち除外の数。 */
    public int excluded() {
        return kept - conforming;
    }
}
