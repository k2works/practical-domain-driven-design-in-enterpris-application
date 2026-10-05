package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 見積りの社内承認と提示の結果。
 */
public sealed interface PresentationOutcome {

    /**
     * 提示した。
     *
     * @param number 業務番号
     * @param quotationNo 見積り番号
     */
    record Presented(TransportRequestNumber number, int quotationNo) implements PresentationOutcome {

        public Presented {
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 業務の規則で提示しなかった（承認待ちでない）。
     *
     * @param reason 理由
     */
    record Rejected(QuotationRejection reason) implements PresentationOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 業務番号の輸送要求か、見積り番号の見積りがない。 */
    record NotFound() implements PresentationOutcome {}

    /** 読み込んだ後に、ほかの利用者が先に更新した（楽観ロックの競合）。 */
    record Conflict() implements PresentationOutcome {}
}
