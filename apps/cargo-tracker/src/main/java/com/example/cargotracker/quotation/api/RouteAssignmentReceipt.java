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
     * @param reason 理由の名前（このレコードの定数のどれか。公開 API の契約で、見積りのドメインの値の名前とは独立に決める）
     */
    record NotAssigned(String reason) implements RouteAssignmentReceipt {

        /** 見積りが見つからない。 */
        public static final String QUOTATION_NOT_FOUND = "QUOTATION_NOT_FOUND";

        /** 見積りが詳細設計依頼済みでない（作成中・承認待ち・提示済み）。 */
        public static final String NOT_ROUTING_REQUESTED = "NOT_ROUTING_REQUESTED";

        /** 見積りが置換済み・失効（記録）。 */
        public static final String RETIRED = "RETIRED";

        /** 別の経路版をすでに割り当てている（経路版の差し替えは再設計（US-08）で決める）。 */
        public static final String ANOTHER_ROUTE_VERSION_ASSIGNED = "ANOTHER_ROUTE_VERSION_ASSIGNED";

        /** 依頼が不正（区間がない・経路版番号が 1 未満・到着が出発より後でない区間など）。再配信しても変わらないため例外にしない。 */
        public static final String INVALID_REQUEST = "INVALID_REQUEST";

        public NotAssigned {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
