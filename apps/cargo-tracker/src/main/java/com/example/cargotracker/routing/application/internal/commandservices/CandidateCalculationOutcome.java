package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import java.util.Objects;

/**
 * 経路候補の算出の結果（US-06 AC1。Bolt 17）。
 */
public sealed interface CandidateCalculationOutcome {

    /**
     * 候補を算出した。
     *
     * @param number 案件番号
     * @param calculation 算出の結果の件数
     */
    record Calculated(RoutingCaseNumber number, CandidateCalculation calculation)
            implements CandidateCalculationOutcome {

        public Calculated {
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(calculation, "calculation");
        }
    }

    /** 案件番号の案件がない。 */
    record NotFound() implements CandidateCalculationOutcome {}

    /** 読み込んだ後に、ほかの経路設計者が先に算出した（楽観ロックの競合）。 */
    record Conflict() implements CandidateCalculationOutcome {}
}
