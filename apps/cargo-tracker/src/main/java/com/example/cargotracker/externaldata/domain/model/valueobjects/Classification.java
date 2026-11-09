package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.Optional;

/**
 * 分類。受信記録が受け取るたびに一度だけ持つ分類（ED-INV-02、BR-12。data_model の {@code received_record.classification}。
 * Bolt 39b）。差異（{@code DISCREPANCY}）は Bolt 39h で足す。
 */
@ValueObject
public enum Classification {
    /** 採用。新しい冪等性キーの原本。 */
    ADOPTED,
    /** 重複。同じ冪等性キーで、外部原本の採用中の payload のハッシュと同じ原本（Bolt 39d4 の【業務の判断】(f)）。 */
    DUPLICATE,
    /**
     * 隔離。同じ冪等性キーで、外部原本の採用中の payload のハッシュと違う原本。形式の誤りの行とファイルも、外部原本を持たない隔離（キーのない隔離）に
     * なる（UC-15 2a。Bolt 39b2）。
     */
    QUARANTINED;

    /**
     * 既にある外部原本の基準のハッシュ（採用中の payload のハッシュ）と今のハッシュから分類を決める。
     *
     * @param adopted その冪等性キーの外部原本の基準のハッシュ（外部原本がなければ空）
     * @param current 今受け取った原本のハッシュ
     */
    public static Classification classify(Optional<PayloadHash> adopted, PayloadHash current) {
        Objects.requireNonNull(current, "current");
        return adopted.map(base -> base.equals(current) ? DUPLICATE : QUARANTINED)
                .orElse(ADOPTED);
    }
}
