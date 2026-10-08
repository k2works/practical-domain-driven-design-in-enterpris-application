package com.example.cargotracker.quotation.api;

import java.util.Objects;

/**
 * 経路の割当ての結果（見積りの公開 API。Bolt 20）。
 */
public sealed interface RouteAssignmentReceipt {

    /** 割り当てた（見積りは荷主承認待ち）。 */
    record Assigned() implements RouteAssignmentReceipt {}

    /** 同じ経路版をすでに割り当てている（DE-05 の再配信。何もしなかった）。 */
    record AlreadyAssigned() implements RouteAssignmentReceipt {}

    /**
     * 割り当てなかった（業務の理由）。
     *
     * @param reason 理由の名前（QUOTATION_NOT_FOUND、NOT_ROUTING_REQUESTED、RETIRED、ANOTHER_ROUTE_VERSION_ASSIGNED）
     */
    record NotAssigned(String reason) implements RouteAssignmentReceipt {

        /** 見積りが見つからない。 */
        public static final String QUOTATION_NOT_FOUND = "QUOTATION_NOT_FOUND";

        public NotAssigned {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
