package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Objects;

/**
 * 審査（確定・差戻し）の結果。受け付けなかったことも、利用者が受け止める業務の結果として戻り値で返す。
 */
public sealed interface ReviewOutcome {

    /**
     * 審査を受け付けた。
     *
     * @param number 業務番号
     * @param versionNo 審査した版番号
     * @param decision 判断
     */
    record Reviewed(TransportRequestNumber number, int versionNo, ReviewDecision decision) implements ReviewOutcome {

        public Reviewed {
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(decision, "decision");
        }
    }

    /**
     * 業務の規則で受け付けなかった。
     *
     * @param reason 理由
     * @param currentVersionNo 現在の版番号（古い版のときは、この版の再審査を求める）
     */
    record Rejected(ReviewRejection reason, int currentVersionNo) implements ReviewOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 業務番号の輸送要求がない。 */
    record NotFound() implements ReviewOutcome {}

    /** 読み込んだ後に、ほかの利用者が先に更新した（楽観ロックの競合）。開き直して再操作する。 */
    record Conflict() implements ReviewOutcome {}
}
