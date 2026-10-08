package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 荷主の見積りと経路の承認の結果（US-24 AC4・AC5。Bolt 20）。
 */
public sealed interface ShipperApprovalOutcome {

    /**
     * 承認した（見積りは承認済み。DE-04 を発行した）。
     *
     * @param number 業務番号
     * @param quotationNo 見積り番号
     */
    record Approved(TransportRequestNumber number, int quotationNo) implements ShipperApprovalOutcome {

        public Approved {
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 業務の規則で受け付けなかった（失効・置換済み・承認済み・割当ての前）。
     *
     * @param reason 理由
     */
    record Rejected(QuotationRejection reason) implements ShipperApprovalOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 自社の業務番号の輸送要求か、荷主に提示した見積り番号の見積りがない。 */
    record NotFound() implements ShipperApprovalOutcome {}

    /** 読み込んだ後に、ほかの利用者が先に更新した（楽観ロックの競合）。 */
    record Conflict() implements ShipperApprovalOutcome {}
}
