package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 見積りの算出の結果。受け付けなかったことも、利用者が受け止める業務の結果として戻り値で返す。
 */
public sealed interface CalculationOutcome {

    /**
     * 見積りを作って算出した（承認待ち）。
     *
     * @param number 業務番号
     * @param quotationNo 見積り番号
     */
    record Calculated(TransportRequestNumber number, int quotationNo) implements CalculationOutcome {

        public Calculated {
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 入力に不足や誤りがあり、見積りを作らなかった（AC3）。
     *
     * @param violations 不足と誤り
     */
    record Invalid(QuotationViolations violations) implements CalculationOutcome {

        public Invalid {
            Objects.requireNonNull(violations, "violations");
        }
    }

    /**
     * 業務の規則で見積りを作れなかった（Q-INV-18）。
     *
     * @param reason 理由
     */
    record Rejected(QuotationRejection reason) implements CalculationOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 業務番号の輸送要求がない。 */
    record NotFound() implements CalculationOutcome {}
}
