package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.List;
import java.util.Objects;

/**
 * 提出の検証結果。提出の検証で見つかった不足と誤りの一覧（Q-INV-01、Q-INV-02、Q-INV-12、Q-INV-16）。
 * 理由と直し方の文言は画面の層が付ける。ここでは項目と理由の種類だけを持つ。
 *
 * @param violations 不足と誤り（空なら提出できる）
 */
@ValueObject
public record SubmissionViolations(List<Violation> violations) {

    public SubmissionViolations {
        violations = List.copyOf(violations);
    }

    /** 違反がないか。 */
    public boolean isEmpty() {
        return violations.isEmpty();
    }

    /** 項目にその理由の違反があるか。 */
    public boolean has(Item item, Reason reason) {
        return violations.contains(new Violation(item, reason));
    }

    /**
     * 1 つの不足または誤り。
     *
     * @param item 項目
     * @param reason 理由の種類
     */
    public record Violation(Item item, Reason reason) {

        public Violation {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 提出の検証の対象の項目。 */
    public enum Item {
        /** 荷受人。 */
        CONSIGNEE,
        /** 出発地。 */
        ORIGIN,
        /** 目的地。 */
        DESTINATION,
        /** 希望到着期限。 */
        ARRIVAL_DEADLINE,
        /** 貨物種別。 */
        CARGO_CATEGORY,
        /** 荷姿。 */
        PACKAGE_TYPE,
        /** 個数。 */
        PACKAGE_COUNT,
        /** 総重量（kg）。 */
        GROSS_WEIGHT_KG,
        /** 容積（m3）。 */
        VOLUME_M3,
        /** 商業送り状（Q-INV-16）。 */
        COMMERCIAL_INVOICE,
        /** 梱包明細（Q-INV-16）。 */
        PACKING_LIST,
        /** その他の書類（Q-INV-16）。 */
        OTHER_DOCUMENTS
    }

    /** 違反の理由の種類。 */
    public enum Reason {
        /** 入力がない（Q-INV-01）。 */
        MISSING,
        /** 目的地が出発地と同じ（D-6）。 */
        SAME_AS_ORIGIN,
        /** 希望到着期限が提出時刻以前（Q-INV-12）。 */
        NOT_AFTER_SUBMISSION,
        /** 個数・総重量・容積が 0 以下（Q-INV-12）。 */
        NOT_POSITIVE,
        /** 総重量・容積の小数点以下が 3 桁を超える（Q-INV-12）。 */
        TOO_MANY_DECIMALS,
        /** 貨物種別が MVP の対象外（Q-INV-02、BR-03）。 */
        OUTSIDE_MVP,
        /** 書類の形式が PDF・PNG・JPEG でない（中身で判定。Q-INV-16）。 */
        UNSUPPORTED_FORMAT,
        /** 書類が 10 MB を超える（Q-INV-16）。 */
        TOO_LARGE,
        /** 書類の件数が種類ごとの上限を超える（Q-INV-16）。 */
        TOO_MANY
    }
}
