package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.validation.BindingResult;

/**
 * 提出の検証結果（不足と誤り）を、画面の項目と「理由と直し方」の文言にする（US-01 AC2、UI 設計 C-03）。
 * ドメインは項目と理由の種類だけを返し、利用者に見せる文言はこの画面の層で決める。
 */
final class SubmissionViolationMessages {

    /** フォームの項目のキー（consignee など）から、エラー要約に出す表示名（荷受人など）への対応。エラー要約のリンクは表示名で始める。 */
    static final Map<String, String> FIELD_LABELS = Arrays.stream(Item.values())
            .collect(Collectors.toUnmodifiableMap(
                    SubmissionViolationMessages::field, SubmissionViolationMessages::label));

    private SubmissionViolationMessages() {}

    /** 違反を、画面の項目の誤りとして BindingResult に記録する。 */
    static void reject(SubmissionViolations violations, BindingResult errors) {
        for (Violation violation : violations.violations()) {
            errors.rejectValue(
                    field(violation.item()), "violation." + violation.reason().name(), message(violation));
        }
    }

    static String field(Item item) {
        return switch (item) {
            case CONSIGNEE -> "consignee";
            case ORIGIN -> "origin";
            case DESTINATION -> "destination";
            case ARRIVAL_DEADLINE -> "arrivalDeadline";
            case CARGO_CATEGORY -> "cargoCategory";
            case PACKAGE_TYPE -> "packageType";
            case PACKAGE_COUNT -> "packageCount";
            case GROSS_WEIGHT_KG -> "grossWeightKg";
            case VOLUME_M3 -> "volumeM3";
        };
    }

    static String label(Item item) {
        return switch (item) {
            case CONSIGNEE -> "荷受人";
            case ORIGIN -> "出発地";
            case DESTINATION -> "目的地";
            case ARRIVAL_DEADLINE -> "希望到着期限";
            case CARGO_CATEGORY -> "貨物種別";
            case PACKAGE_TYPE -> "荷姿";
            case PACKAGE_COUNT -> "個数";
            case GROSS_WEIGHT_KG -> "総重量（kg）";
            case VOLUME_M3 -> "容積（m3）";
        };
    }

    static String message(Violation violation) {
        Item item = violation.item();
        return switch (violation.reason()) {
            case MISSING -> missing(item);
            case SAME_AS_ORIGIN -> "目的地が出発地と同じです。別の港を入力してください";
            case NOT_AFTER_SUBMISSION -> "希望到着期限が過ぎているか、提出する時刻と同じです。提出する時刻より後の日時を入力してください";
            case NOT_POSITIVE ->
                item == Item.PACKAGE_COUNT ? "個数は 1 以上の整数で入力してください" : label(item) + "は 0 より大きい値で入力してください";
            case TOO_MANY_DECIMALS -> label(item) + "は小数点以下 3 桁までで入力してください";
            case OUTSIDE_MVP -> CargoCategoryNotice.MESSAGE;
        };
    }

    private static String missing(Item item) {
        return switch (item) {
            case CONSIGNEE, CARGO_CATEGORY, PACKAGE_TYPE -> label(item) + "を選んでください";
            default -> label(item) + "を入力してください";
        };
    }

    /** 特殊貨物の案内（Q-INV-02、BR-03）。画面の案内とエラー要約で同じ文言を使う。 */
    static final class CargoCategoryNotice {

        static final String MESSAGE = "危険物・冷凍・その他特殊の貨物は、この画面では受け付けていません。担当の営業窓口へご相談ください";

        private CargoCategoryNotice() {}
    }
}
