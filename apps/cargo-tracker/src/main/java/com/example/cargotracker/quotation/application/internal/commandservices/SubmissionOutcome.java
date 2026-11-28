package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 輸送要求の提出の結果。提出できたか、不足や誤りで受け付けなかったかのどちらか。
 * 受け付けなかったことは利用者が直せる業務の結果なので、例外ではなく戻り値で返す。
 */
public sealed interface SubmissionOutcome {

    /**
     * 提出できた。
     *
     * @param transportRequestId 輸送要求 ID
     * @param number 振った業務番号
     */
    record Submitted(TransportRequestId transportRequestId, TransportRequestNumber number)
            implements SubmissionOutcome {

        public Submitted {
            Objects.requireNonNull(transportRequestId, "transportRequestId");
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 不足や誤りがあり、受け付けなかった。輸送要求は作らず、業務番号も振らない。
     *
     * @param violations 不足と誤り
     */
    record Rejected(SubmissionViolations violations) implements SubmissionOutcome {

        public Rejected {
            Objects.requireNonNull(violations, "violations");
            if (violations.isEmpty()) {
                throw new IllegalArgumentException("違反のない提出を受け付けないことはできません");
            }
        }
    }
}
