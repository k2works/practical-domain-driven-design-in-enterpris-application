package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Violation;
import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 見積りの入力。営業担当者が算出しようとする料金明細・通貨・有効期限・経路方針。項目が欠けていてよく、
 * 検証して違反がなければ、料金根拠・有効期限・経路方針になる（Q-INV-05・17）。違反はまとめて返す（1 件ずつ直させない）。
 *
 * @param lines 料金明細の入力（入力の順）
 * @param currency 通貨（なければ null）
 * @param expiresAt 有効期限（なければ null）
 * @param via 主な経由地
 * @param departureAt 概算の出発日時（なければ null）
 * @param arrivalAt 概算の到着日時（なければ null）
 */
@ValueObject
public record QuotationInput(
        List<PricingLineInput> lines,
        Currency currency,
        UtcInstant expiresAt,
        List<Location> via,
        UtcInstant departureAt,
        UtcInstant arrivalAt) {

    private static final int MAX_TEXT = 200;
    private static final int AMOUNT_SCALE = 2;

    public QuotationInput {
        lines = List.copyOf(lines);
        via = List.copyOf(via);
    }

    /**
     * 算出の時刻で検証する。有効期限は算出の時刻より後でなければならない（同時刻と過去は誤り。BR-10）。
     *
     * @param calculatedAt 算出の時刻
     */
    public Validation validate(UtcInstant calculatedAt) {
        List<Violation> violations = new ArrayList<>();
        validateLines(violations);
        if (currency == null) {
            violations.add(new Violation(Item.CURRENCY, Reason.MISSING));
        }
        if (expiresAt == null) {
            violations.add(new Violation(Item.EXPIRES_AT, Reason.MISSING));
        } else if (!expiresAt.instant().isAfter(calculatedAt.instant())) {
            violations.add(new Violation(Item.EXPIRES_AT, Reason.NOT_AFTER_CALCULATION));
        }
        if (via.size() > RoutePolicy.MAX_VIA) {
            violations.add(new Violation(Item.ROUTE_VIA, Reason.TOO_MANY));
        }
        if (departureAt == null) {
            violations.add(new Violation(Item.DEPARTURE_AT, Reason.MISSING));
        }
        if (arrivalAt == null) {
            violations.add(new Violation(Item.ARRIVAL_AT, Reason.MISSING));
        } else if (departureAt != null && !arrivalAt.instant().isAfter(departureAt.instant())) {
            violations.add(new Violation(Item.ARRIVAL_AT, Reason.NOT_AFTER_DEPARTURE));
        }
        if (!violations.isEmpty()) {
            return new Invalid(new QuotationViolations(violations));
        }
        return new Valid(
                new PricingBasis(lines.stream().map(QuotationInput::toLine).toList(), currency),
                new QuotationExpiry(expiresAt),
                new RoutePolicy(via, departureAt, arrivalAt));
    }

    private void validateLines(List<Violation> violations) {
        if (lines.isEmpty()) {
            violations.add(new Violation(Item.PRICING_LINES, Reason.MISSING));
            return;
        }
        if (lines.size() > PricingBasis.MAX_LINES) {
            violations.add(new Violation(Item.PRICING_LINES, Reason.TOO_MANY));
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            validateLine(lines.get(i), i + 1, violations);
        }
    }

    private static void validateLine(PricingLineInput line, int lineNo, List<Violation> violations) {
        String description = line.description();
        if (description == null || description.isBlank()) {
            violations.add(new Violation(Item.PRICING_LINES, Reason.DESCRIPTION_MISSING, lineNo));
        } else if (description.length() > MAX_TEXT) {
            violations.add(new Violation(Item.PRICING_LINES, Reason.DESCRIPTION_TOO_LONG, lineNo));
        }
        BigDecimal amount = line.amount();
        if (amount == null) {
            violations.add(new Violation(Item.PRICING_LINES, Reason.AMOUNT_MISSING, lineNo));
        } else if (amount.signum() <= 0) {
            violations.add(new Violation(Item.PRICING_LINES, Reason.AMOUNT_NOT_POSITIVE, lineNo));
        } else if (amount.stripTrailingZeros().scale() > AMOUNT_SCALE) {
            violations.add(new Violation(Item.PRICING_LINES, Reason.AMOUNT_TOO_MANY_DECIMALS, lineNo));
        }
        String reference = line.contractReference();
        if (reference != null && reference.length() > MAX_TEXT) {
            violations.add(new Violation(Item.PRICING_LINES, Reason.REFERENCE_TOO_LONG, lineNo));
        }
    }

    private static PricingLine toLine(PricingLineInput line) {
        String reference = line.contractReference();
        return new PricingLine(
                line.description().strip(),
                line.amount().setScale(AMOUNT_SCALE),
                reference == null || reference.isBlank() ? null : reference.strip());
    }

    /** 見積りの算出の検証の結果。違反がなければ料金根拠・有効期限・経路方針、あれば違反の一覧のどちらか。 */
    public sealed interface Validation {}

    /**
     * 違反がなく、料金根拠・有効期限・経路方針になった。
     *
     * @param pricingBasis 料金根拠
     * @param expiry 有効期限
     * @param routePolicy 経路方針
     */
    public record Valid(PricingBasis pricingBasis, QuotationExpiry expiry, RoutePolicy routePolicy)
            implements Validation {}

    /**
     * 不足や誤りがあった。
     *
     * @param violations 不足と誤り（空でない）
     */
    public record Invalid(QuotationViolations violations) implements Validation {}
}
