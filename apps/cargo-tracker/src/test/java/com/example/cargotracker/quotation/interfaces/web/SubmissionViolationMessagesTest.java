package com.example.cargotracker.quotation.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapperImpl;

/**
 * 提出の検証結果を「理由と直し方」の文言にする対応を、項目と理由のすべての組で確かめる（US-01 AC2、Bolt 4 レビュー R-06）。
 */
class SubmissionViolationMessagesTest {

    static Stream<Arguments> allViolations() {
        return Stream.of(Item.values())
                .flatMap(item -> Stream.of(Reason.values()).map(reason -> Arguments.of(item, reason)));
    }

    @ParameterizedTest
    @MethodSource("allViolations")
    void どの違反にも直し方を含む文言がある(Item item, Reason reason) {
        String message = SubmissionViolationMessages.message(new Violation(item, reason));

        assertThat(message).isNotBlank().endsWith("ください");
    }

    @ParameterizedTest
    @EnumSource(Item.class)
    void 不足の文言は項目の名前で始まる(Item item) {
        assertThat(SubmissionViolationMessages.message(new Violation(item, Reason.MISSING)))
                .startsWith(SubmissionViolationMessages.label(item));
    }

    @ParameterizedTest
    @EnumSource(Item.class)
    void どの項目も画面の入力の項目に対応しエラー要約の表示名がある(Item item) {
        String field = SubmissionViolationMessages.field(item);

        assertThat(new BeanWrapperImpl(new TransportRequestForm()).isReadableProperty(field))
                .as("入力の項目 %s", field)
                .isTrue();
        assertThat(SubmissionViolationMessages.FIELD_LABELS)
                .containsEntry(field, SubmissionViolationMessages.label(item));
    }
}
