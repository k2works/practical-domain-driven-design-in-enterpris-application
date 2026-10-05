package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.domain.model.entities.TransportRequestVersion;
import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 荷主の画面（C-04）と社内の画面（S-03）で共通の、輸送要求の表示の部品（Bolt 6〜8 レビュー R-12）。
 * 日時の表し方（荷主は利用者のタイムゾーン、社内は UTC を併記）だけを呼び出し側が渡す。
 */
final class TransportRequestViews {

    private static final String UNKNOWN_CONSIGNEE = "（仮の一覧にない荷受人）";

    private TransportRequestViews() {}

    /** 輸送条件と提出時刻の表示の行（見出しと値。表示の順）。 */
    static Map<String, String> termsRows(
            TransportRequestVersion version,
            ProvisionalConsigneeProperties consignees,
            Function<UtcInstant, String> dateTime) {
        ShipmentTerms terms = version.terms();
        Cargo cargo = terms.cargo();
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("荷受人", consigneeName(terms, consignees));
        rows.put("出発地", terms.origin().unLocode());
        rows.put("目的地", terms.destination().unLocode());
        rows.put("希望到着期限", dateTime.apply(terms.arrivalDeadline()));
        rows.put("貨物種別", TransportRequestLabels.cargoCategory(cargo.category()));
        rows.put("荷姿", TransportRequestLabels.packageType(cargo.packageType()));
        rows.put("個数", String.valueOf(cargo.packageCount()));
        rows.put("総重量（kg）", cargo.grossWeightKg().stripTrailingZeros().toPlainString());
        rows.put("容積（m3）", cargo.volumeM3().stripTrailingZeros().toPlainString());
        rows.put("提出時刻", dateTime.apply(version.submittedAt()));
        return rows;
    }

    /** 荷受人の名前。企業マスターができるまでは設定の仮の一覧から引く（US-16 で置き換える）。 */
    static String consigneeName(ShipmentTerms terms, ProvisionalConsigneeProperties consignees) {
        return consignees
                .find(terms.consigneeCompanyId().value())
                .map(ProvisionalConsigneeProperties.Company::name)
                .orElse(UNKNOWN_CONSIGNEE);
    }

    /** URL の業務番号を解釈する。形式でなければ空（見つからない扱いにする）。 */
    static Optional<TransportRequestNumber> parseNumber(String number) {
        try {
            return Optional.of(TransportRequestNumber.parse(number));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
