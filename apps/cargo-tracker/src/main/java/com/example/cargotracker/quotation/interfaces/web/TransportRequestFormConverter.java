package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.validation.BindingResult;

/**
 * 見積依頼の作成画面の入力を、輸送条件の入力（{@link ShipmentTermsInput}）に変換する。
 * ここでは形式（UN/LOCODE、日時、数値、一覧の値）だけを確かめる。空欄は入力なし（null）として渡し、
 * 不足や業務の規則の誤りはドメインが判定する。形式の誤りはすべて BindingResult に記録してから返す（1 件ずつ直させない）。
 */
final class TransportRequestFormConverter {

    /** 荷主の画面のタイムゾーン（画面の共通の表示と同じ）。 */
    static final ZoneId CUSTOMER_ZONE = TransportRequestLabels.DISPLAY_ZONE;

    static final String DEADLINE_PATTERN = "uuuu-MM-dd HH:mm";

    private static final DateTimeFormatter DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern(DEADLINE_PATTERN).withResolverStyle(ResolverStyle.STRICT);
    private static final String LOCATION_FORMAT_MESSAGE = "UN/LOCODE（国コード 2 文字 + 地点コード 3 文字、例: JPTYO）で入力してください";

    private TransportRequestFormConverter() {}

    /**
     * 入力を変換する。形式の誤りがあれば BindingResult に記録して空を返す。
     */
    static Optional<ShipmentTermsInput> convert(
            TransportRequestForm form, ProvisionalConsigneeProperties consignees, BindingResult errors) {
        ShipmentTermsInput input = new ShipmentTermsInput(
                parse(form.getConsignee(), "consignee", errors, "選択肢から選んでください", text -> {
                    UUID id = UUID.fromString(text);
                    return consignees
                            .find(id)
                            .map(company -> new CompanyId(company.id()))
                            .orElseThrow();
                }),
                parse(
                        form.getOrigin(),
                        "origin",
                        errors,
                        LOCATION_FORMAT_MESSAGE,
                        TransportRequestFormConverter::location),
                parse(
                        form.getDestination(),
                        "destination",
                        errors,
                        LOCATION_FORMAT_MESSAGE,
                        TransportRequestFormConverter::location),
                parse(
                        form.getArrivalDeadline(),
                        "arrivalDeadline",
                        errors,
                        "2026-11-02 09:00 の形（日本時間）で入力してください",
                        TransportRequestFormConverter::deadline),
                parse(form.getCargoCategory(), "cargoCategory", errors, "選択肢から選んでください", CargoCategory::valueOf),
                parse(form.getPackageType(), "packageType", errors, "選択肢から選んでください", PackageType::valueOf),
                parse(form.getPackageCount(), "packageCount", errors, "1 以上の整数で入力してください", Integer::valueOf),
                parse(form.getGrossWeightKg(), "grossWeightKg", errors, "数字で入力してください", BigDecimal::new),
                parse(form.getVolumeM3(), "volumeM3", errors, "数字で入力してください", BigDecimal::new));
        return errors.hasErrors() ? Optional.empty() : Optional.of(input);
    }

    /**
     * 現在の版の輸送条件を、編集画面（出し直し）の初期値にする。希望到着期限は荷主の画面のタイムゾーンで示す。
     */
    static TransportRequestForm toForm(ShipmentTerms terms) {
        Cargo cargo = terms.cargo();
        TransportRequestForm form = new TransportRequestForm();
        form.setConsignee(terms.consigneeCompanyId().value().toString());
        form.setOrigin(terms.origin().unLocode());
        form.setDestination(terms.destination().unLocode());
        form.setArrivalDeadline(
                DEADLINE_FORMAT.format(terms.arrivalDeadline().instant().atZone(CUSTOMER_ZONE)));
        form.setCargoCategory(cargo.category().name());
        form.setPackageType(cargo.packageType().name());
        form.setPackageCount(String.valueOf(cargo.packageCount()));
        form.setGrossWeightKg(cargo.grossWeightKg().stripTrailingZeros().toPlainString());
        form.setVolumeM3(cargo.volumeM3().stripTrailingZeros().toPlainString());
        return form;
    }

    /** 空欄は null（入力なし）にする。形式の誤りは記録して null にする。 */
    private static <T> T parse(
            String value, String field, BindingResult errors, String formatMessage, Function<String, T> parser) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return parser.apply(text);
        } catch (IllegalArgumentException | NoSuchElementException _) {
            // 形式の誤り（UUID・UN/LOCODE・日時・数値・選択肢の値）と、仮の一覧にない荷受人だけを入力の誤りにする。
            // それ以外の例外は実装の不具合なので、入力の誤りに見せずにそのまま投げる（Bolt 4 レビュー R-13）
            errors.rejectValue(field, field + ".format", formatMessage);
            return null;
        }
    }

    /** 貼り付けで付く前後の空白を除き、小文字は大文字にそろえる。形式の検証はドメインの場所に任せる。 */
    private static Location location(String text) {
        return new Location(text.toUpperCase(Locale.ROOT));
    }

    private static UtcInstant deadline(String text) {
        try {
            return new UtcInstant(LocalDateTime.parse(text, DEADLINE_FORMAT)
                    .atZone(CUSTOMER_ZONE)
                    .toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
