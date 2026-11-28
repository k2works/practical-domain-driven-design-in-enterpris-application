package com.example.cargotracker.quotation.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Violation;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 見積りの算出の誤りの文言（Q-INV-17）。どの組み合わせにも直し方を含む文言があり、表示名で始めない（R-04）。 */
class QuotationViolationMessagesTest {

    static Stream<Arguments> allViolations() {
        return Arrays.stream(Item.values())
                .flatMap(item -> Arrays.stream(Reason.values()).map(reason -> Arguments.of(item, reason)));
    }

    @ParameterizedTest
    @MethodSource("allViolations")
    void どの違反にも直し方を含む文言がある(Item item, Reason reason) {
        assertThat(QuotationViolationMessages.message(new Violation(item, reason)))
                .isNotBlank()
                .endsWith("ください");
    }
}
