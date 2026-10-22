package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import java.util.List;
import java.util.Objects;

/**
 * 確定した経路の評価の結果（US-07 AC4。Bolt 37d2）。
 */
public sealed interface ConfirmedRouteEvaluationOutcome {

    /**
     * 経路版を再設計要にし、DE-06 を発行した。
     *
     * @param number 案件番号
     * @param routeVersionNo 再設計要にした経路版番号
     * @param reasons 理由の区分
     */
    record RedesignRequired(RoutingCaseNumber number, int routeVersionNo, List<RedesignReasonCode> reasons)
            implements ConfirmedRouteEvaluationOutcome {

        public RedesignRequired {
            Objects.requireNonNull(number, "number");
            reasons = List.copyOf(reasons);
        }
    }

    /** 再設計要にしなかった（最新の経路版が確定でない、区間の情報版が変わらない、今の予定でも適合する。保存しない）。 */
    record Unchanged() implements ConfirmedRouteEvaluationOutcome {}

    /** 案件番号の案件がない。 */
    record NotFound() implements ConfirmedRouteEvaluationOutcome {}

    /**
     * 読み込んだ後に案件が更新された（楽観ロックの競合）。最新が確定の案件を書くのは今は評価だけなので、先の評価がすでに再設計要にして
     * DE-06 を出している。再試行は要らない（US-08 の再設計で書き手が増えたら見直す）。
     */
    record Conflict() implements ConfirmedRouteEvaluationOutcome {}
}
