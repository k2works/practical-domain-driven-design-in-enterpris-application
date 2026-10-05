package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Reason;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 見積りの入力の検証（Q-INV-05・17、2026-10-05 の決定。Bolt 10）。 */
class QuotationInputTest {

    private static final UtcInstant CALCULATED_AT = at("2026-10-05T04:00:00Z");
    private static final PricingLineInput FREIGHT =
            new PricingLineInput("海上運賃", new BigDecimal("3200.00"), "年間契約 2026-A");

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    private static QuotationInput input(List<PricingLineInput> lines) {
        return new QuotationInput(
                lines,
                Currency.USD,
                at("2026-10-08T09:00:00Z"),
                List.of(new Location("SGSIN")),
                at("2026-10-10T00:00:00Z"),
                at("2026-10-30T09:00:00Z"));
    }

    private static QuotationViolations violations(QuotationInput input) {
        return ((QuotationInput.Invalid) input.validate(CALCULATED_AT)).violations();
    }

    @Test
    void そろった入力は料金根拠と有効期限と経路方針になる() {
        QuotationInput.Validation validation = input(
                        List.of(FREIGHT, new PricingLineInput("燃料調整金", new BigDecimal("530.00"), null)))
                .validate(CALCULATED_AT);

        assertThat(validation).isInstanceOfSatisfying(QuotationInput.Valid.class, valid -> {
            assertThat(valid.pricingBasis().total()).isEqualByComparingTo("3730.00");
            assertThat(valid.pricingBasis().currency()).isEqualTo(Currency.USD);
            assertThat(valid.expiry().expiresAt()).isEqualTo(at("2026-10-08T09:00:00Z"));
            assertThat(valid.routePolicy().via()).containsExactly(new Location("SGSIN"));
        });
    }

    @Test
    void 何も入れなければ不足をまとめて返す() {
        QuotationInput empty = new QuotationInput(List.of(), null, null, List.of(), null, null);

        assertThat(violations(empty).violations())
                .extracting(QuotationViolations.Violation::item, QuotationViolations.Violation::reason)
                .containsExactlyInAnyOrder(
                        tuple(Item.PRICING_LINES, Reason.MISSING),
                        tuple(Item.CURRENCY, Reason.MISSING),
                        tuple(Item.EXPIRES_AT, Reason.MISSING),
                        tuple(Item.DEPARTURE_AT, Reason.MISSING),
                        tuple(Item.ARRIVAL_AT, Reason.MISSING));
    }

    @Test
    void 料金明細は10行まで受け付け11行は多すぎる() {
        assertThat(input(Collections.nCopies(10, FREIGHT)).validate(CALCULATED_AT))
                .isInstanceOf(QuotationInput.Valid.class);
        assertThat(violations(input(Collections.nCopies(11, FREIGHT))).has(Item.PRICING_LINES, Reason.TOO_MANY))
                .isTrue();
    }

    @Test
    void 明細の内容と金額と参照した契約条件の誤りを行の番号とともに返す() {
        QuotationViolations violations = violations(input(List.of(
                new PricingLineInput("", new BigDecimal("1.00"), null),
                new PricingLineInput("x".repeat(201), BigDecimal.ZERO, "y".repeat(201)),
                new PricingLineInput("割増", new BigDecimal("1.001"), null),
                new PricingLineInput("x".repeat(200), new BigDecimal("0.01"), "y".repeat(200)))));

        assertThat(violations.violations())
                .containsExactlyInAnyOrder(
                        new QuotationViolations.Violation(Item.PRICING_LINES, Reason.DESCRIPTION_MISSING, 1),
                        new QuotationViolations.Violation(Item.PRICING_LINES, Reason.DESCRIPTION_TOO_LONG, 2),
                        new QuotationViolations.Violation(Item.PRICING_LINES, Reason.AMOUNT_NOT_POSITIVE, 2),
                        new QuotationViolations.Violation(Item.PRICING_LINES, Reason.REFERENCE_TOO_LONG, 2),
                        new QuotationViolations.Violation(Item.PRICING_LINES, Reason.AMOUNT_TOO_MANY_DECIMALS, 3));
    }

    @Test
    void 金額のない明細は不足とする() {
        assertThat(violations(input(List.of(new PricingLineInput("海上運賃", null, null))))
                        .violations())
                .containsExactly(new QuotationViolations.Violation(Item.PRICING_LINES, Reason.AMOUNT_MISSING, 1));
    }

    @Test
    void 有効期限は算出の時刻より後でなければならない() {
        QuotationInput same = new QuotationInput(
                List.of(FREIGHT),
                Currency.USD,
                CALCULATED_AT,
                List.of(),
                at("2026-10-10T00:00:00Z"),
                at("2026-10-30T09:00:00Z"));
        QuotationInput oneSecondLater = new QuotationInput(
                List.of(FREIGHT),
                Currency.USD,
                at("2026-10-05T04:00:01Z"),
                List.of(),
                at("2026-10-10T00:00:00Z"),
                at("2026-10-30T09:00:00Z"));

        assertThat(violations(same).has(Item.EXPIRES_AT, Reason.NOT_AFTER_CALCULATION))
                .isTrue();
        assertThat(oneSecondLater.validate(CALCULATED_AT)).isInstanceOf(QuotationInput.Valid.class);
    }

    @Test
    void 経由地は5件まで受け付け6件は多すぎる() {
        List<Location> five = List.of(
                new Location("SGSIN"),
                new Location("CNSHA"),
                new Location("LKCMB"),
                new Location("AEJEA"),
                new Location("EGPSD"));
        List<Location> six = List.of(
                new Location("SGSIN"),
                new Location("CNSHA"),
                new Location("LKCMB"),
                new Location("AEJEA"),
                new Location("EGPSD"),
                new Location("ESALG"));

        assertThat(withVia(five).validate(CALCULATED_AT)).isInstanceOf(QuotationInput.Valid.class);
        assertThat(violations(withVia(six)).has(Item.ROUTE_VIA, Reason.TOO_MANY))
                .isTrue();
    }

    @Test
    void 概算の到着は出発より後でなければならない() {
        QuotationInput arrivalBeforeDeparture = new QuotationInput(
                List.of(FREIGHT),
                Currency.USD,
                at("2026-10-08T09:00:00Z"),
                List.of(),
                at("2026-10-30T09:00:00Z"),
                at("2026-10-30T09:00:00Z"));

        assertThat(violations(arrivalBeforeDeparture).has(Item.ARRIVAL_AT, Reason.NOT_AFTER_DEPARTURE))
                .isTrue();
    }

    private static QuotationInput withVia(List<Location> via) {
        return new QuotationInput(
                List.of(FREIGHT),
                Currency.USD,
                at("2026-10-08T09:00:00Z"),
                via,
                at("2026-10-10T00:00:00Z"),
                at("2026-10-30T09:00:00Z"));
    }
}
