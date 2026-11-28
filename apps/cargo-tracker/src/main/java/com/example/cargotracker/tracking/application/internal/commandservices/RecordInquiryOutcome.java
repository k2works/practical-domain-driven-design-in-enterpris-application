package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 照会の記録の結果（US-11 AC2。Bolt 40-15）。
 */
public sealed interface RecordInquiryOutcome {

    /**
     * 記録した（引き継いだときは有人案件の受付番号と期限も返す）。
     *
     * @param id 照会記録 ID
     * @param caseNumber 引き継いだ有人案件の受付番号（引き継がなければ空）
     * @param existingCase 未終結の同じ内容の既存の案件に引き継いだか（新しく起票していない）
     * @param answerDueAt 回答期限（新しく起票したときだけ）
     * @param nextUpdateAt 次回更新日時（新しく起票したときだけ）
     */
    record Recorded(
            UUID id,
            Optional<CaseNumber> caseNumber,
            boolean existingCase,
            Optional<UtcInstant> answerDueAt,
            Optional<UtcInstant> nextUpdateAt)
            implements RecordInquiryOutcome {
        public Recorded {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(caseNumber, "caseNumber");
            Objects.requireNonNull(answerDueAt, "answerDueAt");
            Objects.requireNonNull(nextUpdateAt, "nextUpdateAt");
        }
    }

    /** カスタマーサポートでない。 */
    record NotPermitted() implements RecordInquiryOutcome {}

    /** 追跡番号の追跡記録がない、または担当企業の範囲の外（存在を明かさない）。 */
    record TrackingNotFound() implements RecordInquiryOutcome {}

    /**
     * 引き継ぐのに必須項目が足りない（呼び手が入力を保持して示す）。何も書いていない。
     *
     * @param missing 不足の項目
     */
    record Incomplete(Set<MissingItem> missing) implements RecordInquiryOutcome {
        public Incomplete {
            missing = Set.copyOf(missing);
        }
    }

    /** 営業カレンダーが未登録で期限を計算できない。何も書いていない。 */
    record CalendarNotRegistered() implements RecordInquiryOutcome {}
}
