package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Violation;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.validation.BindingResult;

/**
 * 見積りの算出の検証結果（Q-INV-17）を、画面の項目と「理由と直し方」の文言にする（S-04）。
 * エラー要約は「表示名: 文言」の形なので、文言を表示名で始めない（R-04。2026-10-05 の決定）。
 */
final class QuotationViolationMessages {

    /** 料金明細の欄のフォームの項目のキーの始まり（{@code lines[0].description} など）。 */
    static final String LINES_PREFIX = "lines[";

    private QuotationViolationMessages() {}

    /** エラー要約に出す表示名（フォームの項目のキーから）。料金明細の欄は行ごとに名前を付ける。 */
    static Map<String, String> fieldLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("lines", "料金明細");
        for (int row = 0; row < QuotationForm.LINE_ROWS; row++) {
            String line = "明細 " + (row + 1) + " の";
            labels.put(LINES_PREFIX + row + "].description", line + "内容");
            labels.put(LINES_PREFIX + row + "].amount", line + "金額");
            labels.put(LINES_PREFIX + row + "].contractReference", line + "参照した契約条件");
        }
        labels.put("currency", "通貨");
        labels.put("expiresAt", "有効期限");
        labels.put("via", "主な経由地");
        labels.put("departureAt", "概算の出発日時");
        labels.put("arrivalAt", "概算の到着日時");
        return labels;
    }

    /**
     * 違反を、画面の項目の誤りとして BindingResult に記録する。
     *
     * @param rowsOfLines 料金明細の行の番号（1 から）が、画面の何番目の欄だったか（0 から）
     */
    static void reject(QuotationViolations violations, BindingResult errors, List<Integer> rowsOfLines) {
        for (Violation violation : violations.violations()) {
            errors.rejectValue(
                    field(violation, rowsOfLines),
                    "violation." + violation.reason().name(),
                    message(violation));
        }
    }

    /** 料金明細の行でない違反の、フォームの項目のキー。 */
    private static final Map<Item, String> FIELDS = new EnumMap<>(Map.of(
            Item.PRICING_LINES, "lines",
            Item.CURRENCY, "currency",
            Item.EXPIRES_AT, "expiresAt",
            Item.ROUTE_VIA, "via",
            Item.DEPARTURE_AT, "departureAt",
            Item.ARRIVAL_AT, "arrivalAt"));

    static String field(Violation violation, List<Integer> rowsOfLines) {
        if (violation.item() == Item.PRICING_LINES && violation.lineNo() > 0) {
            return lineField(violation, rowsOfLines.get(violation.lineNo() - 1));
        }
        return FIELDS.get(violation.item());
    }

    /** 料金明細の行の違反を、画面の欄（何番目の行のどの欄か）に戻す。 */
    private static String lineField(Violation violation, int row) {
        String prefix = LINES_PREFIX + row + "].";
        return switch (violation.reason()) {
            case AMOUNT_MISSING, AMOUNT_NOT_POSITIVE, AMOUNT_TOO_LARGE, AMOUNT_TOO_MANY_DECIMALS -> prefix + "amount";
            case REFERENCE_TOO_LONG -> prefix + "contractReference";
            default -> prefix + "description";
        };
    }

    private static final String ENTER = "入力してください";

    /** 項目によらず決まる文言。 */
    private static final Map<Reason, String> MESSAGES = new EnumMap<>(Map.of(
            Reason.DESCRIPTION_MISSING, ENTER,
            Reason.AMOUNT_MISSING, ENTER,
            Reason.DESCRIPTION_TOO_LONG, "200 文字までで入力してください",
            Reason.REFERENCE_TOO_LONG, "200 文字までで入力してください",
            Reason.AMOUNT_NOT_POSITIVE, "0 より大きい値で入力してください",
            Reason.AMOUNT_TOO_LARGE, "整数部 13 桁までの金額で入力してください",
            Reason.TOTAL_TOO_LARGE, "合計が整数部 13 桁を超えます。明細の金額を見直してください",
            Reason.AMOUNT_TOO_MANY_DECIMALS, "小数点以下 2 桁までで入力してください",
            Reason.NOT_AFTER_CALCULATION, "いまより後の日時を入力してください",
            Reason.NOT_AFTER_DEPARTURE, "概算の出発日時より後の日時を入力してください"));

    static String message(Violation violation) {
        return switch (violation.reason()) {
            case MISSING -> missing(violation.item());
            case TOO_MANY -> violation.item() == Item.ROUTE_VIA ? "5 件までにしてください" : "10 行までにしてください";
            default -> MESSAGES.get(violation.reason());
        };
    }

    private static String missing(Item item) {
        return switch (item) {
            case PRICING_LINES -> "1 行以上入力してください";
            case CURRENCY -> "選んでください";
            default -> ENTER;
        };
    }
}
