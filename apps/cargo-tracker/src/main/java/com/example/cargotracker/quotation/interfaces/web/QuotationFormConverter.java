package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.domain.model.valueobjects.Currency;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingLineInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.validation.BindingResult;

/**
 * 見積りの作成画面の入力を、見積りの入力（{@link QuotationInput}）に変換する。ここでは形式（数値・日時・UN/LOCODE・通貨の値）だけを確かめ、
 * 不足や業務の規則の誤りはドメインが判定する（Q-INV-17）。形式の誤りはすべて BindingResult に記録してから返す。
 * 空の料金明細の行は無視し、残りの行を上から 1、2… の行の番号として渡す（誤りの行の番号は、画面の欄の位置に戻して示す）。
 */
final class QuotationFormConverter {

    static final String DATE_TIME_FORMAT_MESSAGE = "2026-11-02 09:00 の形（日本時間）で入力してください";
    static final String AMOUNT_FORMAT_MESSAGE = "数字で入力してください";

    /** 3 桁区切りのカンマを付けた金額（例: 1,234,567.50）。区切りの位置が 3 桁ごとでなければ形式の誤り（Bolt 9・10 レビュー R-09）。 */
    private static final Pattern GROUPED_AMOUNT = Pattern.compile("\\d{1,3}(,\\d{3})+(\\.\\d*)?");

    private QuotationFormConverter() {}

    /** 金額の文字列を数にする。3 桁区切りのカンマは除く。 */
    private static BigDecimal amount(String value) {
        return new BigDecimal(GROUPED_AMOUNT.matcher(value).matches() ? value.replace(",", "") : value);
    }

    /**
     * 変換する。形式の誤りがあれば空。
     *
     * @param rowsOfLines 渡した料金明細の行が、画面の何番目の欄だったか（0 から）。誤りを欄に戻すために使う
     */
    static Optional<QuotationInput> convert(QuotationForm form, BindingResult errors, List<Integer> rowsOfLines) {
        List<PricingLineInput> lines = new ArrayList<>();
        for (int row = 0; row < form.getLines().size(); row++) {
            QuotationForm.Line line = form.getLines().get(row);
            if (line.isBlank()) {
                continue;
            }
            rowsOfLines.add(row);
            lines.add(new PricingLineInput(
                    line.getDescription(),
                    TransportRequestFormConverter.parse(
                            line.getAmount(),
                            QuotationViolationMessages.LINES_PREFIX + row + "].amount",
                            errors,
                            AMOUNT_FORMAT_MESSAGE,
                            QuotationFormConverter::amount),
                    line.getContractReference()));
        }
        Currency currency = TransportRequestFormConverter.parse(
                form.getCurrency(),
                "currency",
                errors,
                TransportRequestFormConverter.CHOICE_MESSAGE,
                Currency::valueOf);
        UtcInstant expiresAt = dateTime(form.getExpiresAt(), "expiresAt", errors);
        List<Location> via = via(form.getVia(), errors);
        UtcInstant departureAt = dateTime(form.getDepartureAt(), "departureAt", errors);
        UtcInstant arrivalAt = dateTime(form.getArrivalAt(), "arrivalAt", errors);
        if (errors.hasErrors()) {
            return Optional.empty();
        }
        return Optional.of(new QuotationInput(lines, currency, expiresAt, via, departureAt, arrivalAt));
    }

    private static UtcInstant dateTime(String value, String field, BindingResult errors) {
        return TransportRequestFormConverter.parse(
                value, field, errors, DATE_TIME_FORMAT_MESSAGE, TransportRequestFormConverter::deadline);
    }

    /** 主な経由地。カンマで区切り、前後の空白を除き、小文字は大文字にそろえる。空欄は経由地なし。 */
    private static List<Location> via(String value, BindingResult errors) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Location> locations = TransportRequestFormConverter.parse(
                value,
                "via",
                errors,
                TransportRequestFormConverter.LOCATION_FORMAT_MESSAGE,
                text -> Arrays.stream(text.split(","))
                        .map(String::strip)
                        .filter(code -> !code.isEmpty())
                        .map(TransportRequestFormConverter::location)
                        .toList());
        return locations == null ? List.of() : locations;
    }
}
