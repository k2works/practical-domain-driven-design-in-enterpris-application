package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.RevisionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 本予約前の条件の変更の結果（US-05 AC1。Bolt 62）。
 */
public sealed interface RevisionOutcome {

    /**
     * 変更を受け付け、新しい版が審査中になった。
     *
     * @param number 業務番号（変わらない）
     * @param versionNo 新しい版番号
     */
    record Revised(TransportRequestNumber number, int versionNo) implements RevisionOutcome {

        public Revised {
            Objects.requireNonNull(number, "number");
        }
    }

    /**
     * 輸送条件の入力に不足や誤りがあった（提出と同じ検証）。
     *
     * @param violations 不足と誤り
     */
    record Invalid(SubmissionViolations violations) implements RevisionOutcome {

        public Invalid {
            Objects.requireNonNull(violations, "violations");
        }
    }

    /**
     * 業務の規則で受け付けなかった（変更できない状態、見ていた版が古い）。
     *
     * @param reason 理由
     */
    record Rejected(RevisionRejection reason) implements RevisionOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 荷主企業の輸送要求に、その業務番号がない。 */
    record NotFound() implements RevisionOutcome {}

    /** 読み込んだ後に、旧版の見積りのほかの更新が先に保存された。何も変えていない。 */
    record Conflict() implements RevisionOutcome {}
}
