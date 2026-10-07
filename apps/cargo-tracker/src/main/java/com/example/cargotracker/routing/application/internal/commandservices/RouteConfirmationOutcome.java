package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import java.util.Objects;

/**
 * 経路の確定の結果（US-07 AC1・AC2。Bolt 19）。
 */
public sealed interface RouteConfirmationOutcome {

    /**
     * 経路を確定した。
     *
     * @param number 案件番号
     * @param routeVersionNo 確定した経路版番号
     * @param candidateNo 確定した候補番号
     */
    record Confirmed(RoutingCaseNumber number, int routeVersionNo, int candidateNo)
            implements RouteConfirmationOutcome {

        public Confirmed {
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 確定を拒否した。
     *
     * @param reason 理由
     */
    record Rejected(RouteConfirmationRejectionReason reason) implements RouteConfirmationOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 案件番号の案件がない。 */
    record NotFound() implements RouteConfirmationOutcome {}

    /** 画面を開いた後に、ほかの経路設計者が先に案件を更新した（版の不一致・楽観ロックの競合）。 */
    record Conflict() implements RouteConfirmationOutcome {}
}
