package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.List;
import java.util.Objects;

/**
 * 見積りの算出の検証結果。Q-INV-05・17 の不足と誤りの、項目と理由の種類と、料金明細の行の番号だけを持ち、
 * 利用者に示す理由と直し方の文言は画面の層が付ける。
 *
 * @param violations 不足と誤り（空なら算出できる）
 */
@ValueObject
public record QuotationViolations(List<Violation> violations) {

    public QuotationViolations {
        violations = List.copyOf(violations);
    }

    /** 違反がないか。 */
    public boolean isEmpty() {
        return violations.isEmpty();
    }

    /** 項目にその理由の違反があるか（行の番号は問わない）。 */
    public boolean has(Item item, Reason reason) {
        return violations.stream().anyMatch(violation -> violation.item() == item && violation.reason() == reason);
    }

    /**
     * 1 つの不足または誤り。
     *
     * @param item 項目
     * @param reason 理由の種類
     * @param lineNo 料金明細の行の番号（1 から。明細の行でない違反は 0）
     */
    public record Violation(Item item, Reason reason, int lineNo) {

        public Violation {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(reason, "reason");
        }

        /** 明細の行でない違反。 */
        public Violation(Item item, Reason reason) {
            this(item, reason, 0);
        }
    }

    /** 見積りの算出の検証の対象の項目。 */
    public enum Item {
        /** 料金明細。 */
        PRICING_LINES,
        /** 通貨。 */
        CURRENCY,
        /** 有効期限。 */
        EXPIRES_AT,
        /** 主な経由地。 */
        ROUTE_VIA,
        /** 概算の出発日時。 */
        DEPARTURE_AT,
        /** 概算の到着日時。 */
        ARRIVAL_AT
    }

    /** 不足と誤りの理由の種類。 */
    public enum Reason {
        /** 入力がない（料金明細は 1 行もない）。 */
        MISSING,
        /** 多すぎる（料金明細は 11 行以上、主な経由地は 6 件以上）。 */
        TOO_MANY,
        /** 明細の内容がない。 */
        DESCRIPTION_MISSING,
        /** 明細の内容が 200 文字を超える。 */
        DESCRIPTION_TOO_LONG,
        /** 明細の金額がない。 */
        AMOUNT_MISSING,
        /** 明細の金額が 0 以下。 */
        AMOUNT_NOT_POSITIVE,
        /** 明細の金額が小数点以下 3 桁以上。 */
        AMOUNT_TOO_MANY_DECIMALS,
        /** 参照した契約条件が 200 文字を超える。 */
        REFERENCE_TOO_LONG,
        /** 有効期限が算出の時刻と同じか前（BR-10）。 */
        NOT_AFTER_CALCULATION,
        /** 概算の到着が出発と同じか前。 */
        NOT_AFTER_DEPARTURE
    }
}
