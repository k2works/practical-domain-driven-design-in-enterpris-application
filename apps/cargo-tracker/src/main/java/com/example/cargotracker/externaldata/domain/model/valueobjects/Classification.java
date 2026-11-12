package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.Optional;

/**
 * 分類。受信記録が受け取るたびに一度だけ持つ分類（ED-INV-02・ED-INV-03、BR-12。data_model の {@code received_record.classification}。
 * Bolt 39b。差異は Bolt 39h）。
 */
@ValueObject
public enum Classification {
    /** 採用。新しい冪等性キーの原本で、差異の理由がない。 */
    ADOPTED,
    /** 重複。同じ冪等性キーで、外部原本の採用中の payload のハッシュと同じ原本（Bolt 39d4 の【業務の判断】(f)）。 */
    DUPLICATE,
    /**
     * 隔離。同じ冪等性キーで、外部原本の採用中の payload のハッシュと違う原本。形式の誤りの行とファイルも、外部原本を持たない隔離（キーのない隔離）に
     * なる（UC-15 2a。Bolt 39b2）。
     */
    QUARANTINED,
    /**
     * 差異。同じ外部 ID の採用値と比べて版の逆転か既存の採用値との矛盾があり、自動で採用しない原本（US-14 AC5、ED-INV-03。Bolt 39h）。外部原本と
     * 受信記録は残すが採用値は作らない。
     */
    DISCREPANCY;

    /**
     * 分類を決める（Bolt 39h の開始準備の決定 (6)）。外部原本がなければ、差異の理由があれば差異、なければ採用。その外部版に採用値があれば同じ外部版の
     * 中の比べを先にし、基準と同じハッシュは重複、違えば隔離。その外部版に採用値のない外部原本（差異で作った外部原本）は、理由があればハッシュに
     * よらず差異、なければ基準と比べる（Bolt 39b3 より前の、採用値を持たない外部原本）。
     *
     * @param base その冪等性キーの外部原本の基準のハッシュ（外部原本がなければ空）
     * @param versionAdopted その外部版（冪等性キー）に採用値があるか（外部原本がなければ無視する）
     * @param current 今受け取った原本のハッシュ
     * @param reason 差異の理由（なければ空）
     */
    public static Classification classify(
            Optional<PayloadHash> base,
            boolean versionAdopted,
            PayloadHash current,
            Optional<DiscrepancyReason> reason) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(reason, "reason");
        if (base.isEmpty()) {
            return reason.isPresent() ? DISCREPANCY : ADOPTED;
        }
        if (!versionAdopted && reason.isPresent()) {
            return DISCREPANCY;
        }
        return base.get().equals(current) ? DUPLICATE : QUARANTINED;
    }
}
