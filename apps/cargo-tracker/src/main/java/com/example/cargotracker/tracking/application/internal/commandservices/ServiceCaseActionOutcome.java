package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import java.util.Objects;
import java.util.Set;

/**
 * 有人案件の進捗・回答・終結・再開・回答期限の変更の結果（US-20 AC4〜AC7。Bolt 40-8）。
 */
public sealed interface ServiceCaseActionOutcome {

    /**
     * 行った。
     *
     * @param number 受付番号
     * @param status 行った後の状態
     */
    record Done(CaseNumber number, ServiceCaseStatus status) implements ServiceCaseActionOutcome {
        public Done {
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(status, "status");
        }
    }

    /** その役割はできない。 */
    record NotPermitted() implements ServiceCaseActionOutcome {}

    /** その受付番号の案件がない（荷主担当者に他社の案件を明かさないときも同じ）。 */
    record NotFound() implements ServiceCaseActionOutcome {}

    /**
     * 今の状態ではできない。
     *
     * @param status 今の状態
     */
    record InvalidState(ServiceCaseStatus status) implements ServiceCaseActionOutcome {
        public InvalidState {
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * 入力が正しくない（内容や理由が空、新しい期限が今の期限より後でない、など）。
     *
     * @param message 理由（利用者に示せる文）
     */
    record InvalidInput(String message) implements ServiceCaseActionOutcome {
        public InvalidInput {
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * 復旧照合が未完了なので終結できない（状態は回答済みのまま）。
     *
     * @param missing 不足する照合結果
     */
    record ReconciliationIncomplete(Set<MissingItem> missing) implements ServiceCaseActionOutcome {
        public ReconciliationIncomplete {
            missing = Set.copyOf(missing);
        }
    }

    /** 読み込んだ後にほかの更新が先に保存されていた（開き直して再操作する）。 */
    record Conflict() implements ServiceCaseActionOutcome {}
}
