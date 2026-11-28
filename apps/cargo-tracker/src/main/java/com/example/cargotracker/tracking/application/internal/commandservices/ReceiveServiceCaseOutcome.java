package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import java.util.Objects;

/**
 * 有人案件の受領の結果（US-20 AC2。Bolt 40-7）。
 */
public sealed interface ReceiveServiceCaseOutcome {

    /**
     * 受領した（担当者と受領時刻を記録し、対応中になった）。
     *
     * @param number 受付番号
     */
    record Received(CaseNumber number) implements ReceiveServiceCaseOutcome {
        public Received {
            Objects.requireNonNull(number, "number");
        }
    }

    /** 受領できない役割。 */
    record NotPermitted() implements ReceiveServiceCaseOutcome {}

    /** その受付番号の案件がない。 */
    record NotFound() implements ReceiveServiceCaseOutcome {}

    /**
     * 受領できる状態でない（受付済み・上位対応中のときだけ受領できる）。
     *
     * @param status 今の状態
     */
    record NotReceivable(ServiceCaseStatus status) implements ReceiveServiceCaseOutcome {
        public NotReceivable {
            Objects.requireNonNull(status, "status");
        }
    }

    /** 読み込んだ後にほかの更新が先に保存されていた（開き直して再操作する）。 */
    record Conflict() implements ReceiveServiceCaseOutcome {}
}
