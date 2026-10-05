package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 料金根拠。見積り金額の内訳（料金明細 1〜10 行）と通貨。合計は明細の和（Q-INV-17。Bolt 10）。
 *
 * @param lines 料金明細（入力の順）
 * @param currency 通貨
 */
@ValueObject
public record PricingBasis(List<PricingLine> lines, Currency currency) {

    /** 明細の数の上限。 */
    public static final int MAX_LINES = 10;

    public PricingBasis {
        lines = List.copyOf(lines);
        Objects.requireNonNull(currency, "currency");
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("料金明細は 1〜" + MAX_LINES + " 行です: " + lines.size());
        }
    }

    /** 合計（明細の金額の和）。 */
    public BigDecimal total() {
        return lines.stream().map(PricingLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
