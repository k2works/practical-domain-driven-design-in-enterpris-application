package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.validation.BindingResult;

/**
 * 提出の検証結果（不足と誤り）を、画面の項目と「理由と直し方」の文言にする（US-01 AC2、UI 設計 C-03）。
 * ドメインは項目と理由の種類だけを返し、利用者に見せる文言はこの画面の層で決める。
 */
final class SubmissionViolationMessages {

    /** 項目ごとのフォームの項目のキーと表示名。すべての項目を持つ（SubmissionViolationMessagesTest で確かめる）。 */
    private static final Map<Item, Field> FIELDS = new EnumMap<>(Map.ofEntries(
            Map.entry(Item.CONSIGNEE, new Field("consignee", "荷受人")),
            Map.entry(Item.ORIGIN, new Field("origin", "出発地")),
            Map.entry(Item.DESTINATION, new Field("destination", "目的地")),
            Map.entry(Item.ARRIVAL_DEADLINE, new Field("arrivalDeadline", "希望到着期限")),
            Map.entry(Item.CARGO_CATEGORY, new Field("cargoCategory", "貨物種別")),
            Map.entry(Item.PACKAGE_TYPE, new Field("packageType", "荷姿")),
            Map.entry(Item.PACKAGE_COUNT, new Field("packageCount", "個数")),
            Map.entry(Item.GROSS_WEIGHT_KG, new Field("grossWeightKg", "総重量（kg）")),
            Map.entry(Item.VOLUME_M3, new Field("volumeM3", "容積（m3）")),
            Map.entry(Item.COMMERCIAL_INVOICE, new Field("commercialInvoice", "商業送り状")),
            Map.entry(Item.PACKING_LIST, new Field("packingList", "梱包明細")),
            Map.entry(Item.OTHER_DOCUMENTS, new Field("otherDocuments", "その他の書類"))));

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
        return FIELDS.get(item).key();
    }

    static String label(Item item) {
        return FIELDS.get(item).label();
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
            case UNSUPPORTED_FORMAT, TOO_LARGE, TOO_MANY -> documentMessage(violation);
        };
    }

    /** 必要書類の誤り（Q-INV-16）。ブラウザはファイルの選択を残せないため、選び直しを求める。 */
    private static String documentMessage(Violation violation) {
        Item item = violation.item();
        return switch (violation.reason()) {
            case UNSUPPORTED_FORMAT -> label(item) + "は PDF・PNG・JPEG のファイルを選び直してください";
            case TOO_LARGE -> label(item) + "は 1 件 10 MB までのファイルを選び直してください";
            default -> item == Item.OTHER_DOCUMENTS ? "その他の書類は 3 件までにしてください" : label(item) + "は 1 件までにしてください";
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

    /**
     * フォームの項目。
     *
     * @param key フォームの項目のキー
     * @param label エラー要約に出す表示名
     */
    private record Field(String key, String label) {}
}
