package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 荷主の条件の相談の結果（US-24 AC3。W9 の Bolt 46-1）。
 */
public sealed interface ConsultOutcome {

    /**
     * 相談を受け付けた（相談のイベントを発行した。有人案件の起票は追跡のコンテキストが別のトランザクションで行う）。
     *
     * @param number 業務番号
     * @param quotationNo 見積り番号
     */
    record Requested(TransportRequestNumber number, int quotationNo) implements ConsultOutcome {

        public Requested {
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 業務の規則で受け付けなかった（失効・置換済み・回答済み・辞退済み）。
     *
     * @param reason 理由
     */
    record Rejected(QuotationRejection reason) implements ConsultOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 相談内容が入力の規則に合わない（空・長すぎる）。何も発行しない。
     *
     * @param message 荷主に示す文
     */
    record InvalidContent(String message) implements ConsultOutcome {

        public InvalidContent {
            Objects.requireNonNull(message, "message");
        }
    }

    /** 自社の業務番号の輸送要求か、荷主に提示した見積り番号の見積りがない。 */
    record NotFound() implements ConsultOutcome {}
}
