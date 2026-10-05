package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 再見積りの結果。受け付けなかったことも、利用者が受け止める業務の結果として戻り値で返す（Bolt 11）。
 */
public sealed interface RequotationOutcome {

    /**
     * 旧版を置換済み（または失効）にし、新しい見積りを作って算出した（承認待ち）。
     *
     * @param number 業務番号
     * @param quotationNo 新しい見積りの見積り番号
     * @param previousStatus 旧版の状態（置換済み、または有効期限を過ぎていたので失効）
     */
    record Calculated(TransportRequestNumber number, int quotationNo, QuotationStatus previousStatus)
            implements RequotationOutcome {

        public Calculated {
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(previousStatus, "previousStatus");
        }
    }

    /**
     * 入力に不足や誤りがあり、旧版も変えなかった（AC3 と同じ検証）。
     *
     * @param violations 不足と誤り
     */
    record Invalid(QuotationViolations violations) implements RequotationOutcome {

        public Invalid {
            Objects.requireNonNull(violations, "violations");
        }
    }

    /**
     * 業務の規則で再見積りできなかった（置換済み・失効、見積り作成中でも見積提示済みでもない見積依頼、古い版に対する見積り）。
     *
     * @param reason 理由
     */
    record Rejected(QuotationRejection reason) implements RequotationOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 業務番号の輸送要求か、見積り番号の見積りがない。 */
    record NotFound() implements RequotationOutcome {}

    /** 旧版を読み込んだ後に、ほかの利用者が先に更新した。 */
    record Conflict() implements RequotationOutcome {}
}
