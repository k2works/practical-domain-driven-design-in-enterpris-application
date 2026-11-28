package com.example.cargotracker.quotation.interfaces.api;

import java.util.Objects;

/**
 * 再設計要の伝達の結果（見積りの公開 API。Bolt 37e）。
 */
public sealed interface RouteRedesignNotificationReceipt {

    /**
     * 印を付けた。
     *
     * @param count 新しく付けた見積りの数（すでに付いていた見積りは数えない）
     */
    record Marked(int count) implements RouteRedesignNotificationReceipt {}

    /** その経路版を割り当てた見積りすべてに、すでに付いている（DE-06 の再配信。何もしなかった）。 */
    record AlreadyMarked() implements RouteRedesignNotificationReceipt {}

    /**
     * 付けなかった（業務の理由）。
     *
     * @param reason 理由の名前（このレコードの定数のどれか。公開 API の契約で、見積りのドメインの値の名前とは独立に決める）
     */
    record NotMarked(String reason) implements RouteRedesignNotificationReceipt {

        /** その経路版を割り当てた見積りがない（まだ割り当てていないときも。DE-06 が DE-05 より先に届いた場合は Bolt 37e2 で扱う）。 */
        public static final String QUOTATION_NOT_FOUND = "QUOTATION_NOT_FOUND";

        /** 依頼が不正（案件番号がない・経路版番号が 1 未満・時刻がない）。再配信しても変わらないため例外にしない。 */
        public static final String INVALID_REQUEST = "INVALID_REQUEST";

        public NotMarked {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
