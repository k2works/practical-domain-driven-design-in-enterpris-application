package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 荷主の詳細経路設計へ進む回答の結果（US-24 AC1。Bolt 12）。
 */
public sealed interface RouteDesignRequestOutcome {

    /**
     * 詳細経路設計を依頼した（見積りは詳細設計依頼済み。DE-16 を発行した）。
     *
     * @param number 業務番号
     * @param quotationNo 見積り番号
     */
    record Requested(TransportRequestNumber number, int quotationNo) implements RouteDesignRequestOutcome {

        public Requested {
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 業務の規則で受け付けなかった（失効・置換済み・回答済み）。
     *
     * @param reason 理由
     */
    record Rejected(QuotationRejection reason) implements RouteDesignRequestOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 自社の業務番号の輸送要求か、荷主に提示した見積り番号の見積りがない。 */
    record NotFound() implements RouteDesignRequestOutcome {}

    /** 読み込んだ後に、ほかの利用者が先に更新した（楽観ロックの競合。営業の再見積りとの同時の更新など）。 */
    record Conflict() implements RouteDesignRequestOutcome {}
}
