package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * 料金明細。料金根拠の 1 行（Q-INV-17）。見積りの入力の検証（{@link QuotationInput}）を通った値だけで作る。
 *
 * @param description 内容（200 文字まで）
 * @param amount 金額（0 より大きい、小数点以下 2 桁まで）
 * @param contractReference 参照した契約条件（任意、200 文字まで。なければ null）
 */
@ValueObject
public record PricingLine(String description, BigDecimal amount, String contractReference) {

    public PricingLine {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("金額は 0 より大きい値です: " + amount);
        }
    }

    /** 参照した契約条件。なければ空。 */
    public Optional<String> reference() {
        return Optional.ofNullable(contractReference);
    }
}
