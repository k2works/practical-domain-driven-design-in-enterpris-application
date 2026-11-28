package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 有人案件の起票の結果（US-20 AC1・AC8。Bolt 40-7）。
 */
public sealed interface OpenServiceCaseOutcome {

    /**
     * 起票した。受付番号と次回更新日時を依頼元へ直ちに示す（営業時間外の優先度高は {@code acknowledgedAt} を持つ。AC8）。
     *
     * @param number 受付番号
     * @param receiveDueAt 受領期限
     * @param answerDueAt 回答期限
     * @param nextUpdateAt 次回更新日時
     * @param acknowledgedAt 営業時間外に直ちに返した時刻（営業時間内は空）
     */
    record Opened(
            CaseNumber number,
            UtcInstant receiveDueAt,
            UtcInstant answerDueAt,
            UtcInstant nextUpdateAt,
            Optional<UtcInstant> acknowledgedAt)
            implements OpenServiceCaseOutcome {
        public Opened {
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(receiveDueAt, "receiveDueAt");
            Objects.requireNonNull(answerDueAt, "answerDueAt");
            Objects.requireNonNull(nextUpdateAt, "nextUpdateAt");
            Objects.requireNonNull(acknowledgedAt, "acknowledgedAt");
        }
    }

    /**
     * 必須項目が足りないので引き継がない（呼び手が入力を保持して不足を示す。受付番号は使わない）。
     *
     * @param missing 不足の項目
     */
    record Incomplete(Set<MissingItem> missing) implements OpenServiceCaseOutcome {
        public Incomplete {
            missing = Set.copyOf(missing);
        }
    }

    /**
     * 同じ荷主企業・予約・内容の未終結の案件があるので起票しない。
     *
     * @param existing 既存の受付番号
     */
    record Duplicate(CaseNumber existing) implements OpenServiceCaseOutcome {
        public Duplicate {
            Objects.requireNonNull(existing, "existing");
        }
    }

    /** 起票できない役割、または荷主担当者が他社・対象外の種類を指した。 */
    record NotPermitted() implements OpenServiceCaseOutcome {}

    /** 営業日カレンダーに登録がなく期限を計算できない（SVC-07: 登録漏れ。運用で登録する）。 */
    record CalendarNotRegistered() implements OpenServiceCaseOutcome {}
}
