package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 荷主の辞退の結果（US-24 AC2。W9 の Bolt 46）。
 */
public sealed interface DeclineOutcome {

    /**
     * 辞退を受け付けた（見積りは失効として記録した）。
     *
     * @param number 業務番号
     * @param quotationNo 見積り番号
     */
    record Declined(TransportRequestNumber number, int quotationNo) implements DeclineOutcome {

        public Declined {
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 業務の規則で受け付けなかった（失効・置換済み・回答済み・辞退済み）。
     *
     * @param reason 理由
     */
    record Rejected(QuotationRejection reason) implements DeclineOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 辞退の理由が入力の規則に合わない（空・長すぎる）。何も保存しない。
     *
     * @param message 荷主に示す文
     */
    record InvalidReason(String message) implements DeclineOutcome {

        public InvalidReason {
            Objects.requireNonNull(message, "message");
        }
    }

    /** 自社の業務番号の輸送要求か、荷主に提示した見積り番号の見積りがない。 */
    record NotFound() implements DeclineOutcome {}

    /** 読み込んだ後に、ほかの利用者が先に更新した（楽観ロックの競合）。 */
    record Conflict() implements DeclineOutcome {}
}
