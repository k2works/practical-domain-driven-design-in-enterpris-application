package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.ResubmissionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 再提出の結果。
 */
public sealed interface ResubmissionOutcome {

    /**
     * 再提出を受け付け、新しい版が審査中になった。
     *
     * @param number 業務番号（変わらない）
     * @param versionNo 新しい版番号
     */
    record Resubmitted(TransportRequestNumber number, int versionNo) implements ResubmissionOutcome {

        public Resubmitted {
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 輸送条件の入力に不足や誤りがあった（提出と同じ検証）。
     *
     * @param violations 不足と誤り
     */
    record Invalid(SubmissionViolations violations) implements ResubmissionOutcome {

        public Invalid {
            Objects.requireNonNull(violations, "violations");
        }
    }

    /**
     * 業務の規則で受け付けなかった（下書きでないなど）。
     *
     * @param reason 理由
     */
    record Rejected(ResubmissionRejection reason) implements ResubmissionOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 荷主企業の輸送要求に、その業務番号がない。 */
    record NotFound() implements ResubmissionOutcome {}

    /** 読み込んだ後に、ほかの更新が先に保存された。 */
    record Conflict() implements ResubmissionOutcome {}
}
