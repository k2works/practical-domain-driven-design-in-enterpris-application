package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RecordInquiryCommand;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.validation.BindingResult;

/**
 * S-23 照会の記録の入力を、記録のコマンドに変換する（Bolt 40-15）。ここでは形式（選択肢・追跡番号・日時・長さ）だけを確かめ、業務の規則
 * （担当範囲・必須項目・重複）はサービスが判定する。形式の誤りはすべて BindingResult に記録してから返す（1 件ずつ直させない）。
 */
final class InquiryFormConverter {

    /** 担当部門の既定（案件の担当部門の列は 50 文字まで）。 */
    static final String DEFAULT_DEPARTMENT = "CS";

    /** 画面で選べる経路。Web の 2 つは顧客 Web が作る（W9）。 */
    static final List<InquiryChannel> SELECTABLE_CHANNELS = List.of(InquiryChannel.PHONE, InquiryChannel.EMAIL);

    static final String CHOICE_MESSAGE = "選択肢から選んでください";
    static final String REQUIRED_MESSAGE = "入力してください";

    private static final int CONTENT_LIMIT = 4000;
    private static final int DEPARTMENT_LIMIT = 50;
    private static final DateTimeFormatter RECEIVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private InquiryFormConverter() {}

    /**
     * 入力を変換する。形式の誤りがあれば BindingResult に記録して空を返す。
     *
     * @param now 記録の時刻（受付時刻はこれより後にできない）
     */
    static Optional<RecordInquiryCommand> convert(InquiryForm form, UtcInstant now, BindingResult errors) {
        InquiryChannel channel = required(
                form.getChannel(), "channel", errors, CHOICE_MESSAGE, CHOICE_MESSAGE, InquiryFormConverter::channel);
        TrackingNumber trackingNumber = required(
                form.getTrackingNumber(),
                "trackingNumber",
                errors,
                REQUIRED_MESSAGE,
                "追跡番号の形（CT で始まる 14 文字）で入力してください",
                text -> new TrackingNumber(text.toUpperCase(Locale.ROOT)));
        InquirerKind inquirerKind = required(
                form.getInquirerKind(), "inquirerKind", errors, CHOICE_MESSAGE, CHOICE_MESSAGE, InquirerKind::valueOf);
        Optional<UtcInstant> receivedAt = receivedAt(form.getReceivedAt(), now, errors);
        String content = bounded(form.getContent(), "content", CONTENT_LIMIT, errors);
        String sourceReference =
                bounded(form.getSourceReference(), "sourceReference", Source.REFERENCE_MAX_LENGTH, errors);
        String department = department(form.getDepartment(), errors);
        if (errors.hasErrors()) {
            return Optional.empty();
        }
        return Optional.of(new RecordInquiryCommand(
                channel,
                trackingNumber,
                inquirerKind,
                receivedAt,
                content,
                form.isHandOff(),
                sourceReference.isEmpty() ? Optional.empty() : Optional.of(sourceReference),
                department));
    }

    private static InquiryChannel channel(String text) {
        InquiryChannel channel = InquiryChannel.valueOf(text);
        if (!SELECTABLE_CHANNELS.contains(channel)) {
            throw new IllegalArgumentException("画面で選べない経路: " + text);
        }
        return channel;
    }

    /** 空欄は必須の誤り、形式の誤りは形式の誤りにして null を返す。前後の空白は除く。 */
    private static <T> T required(
            String value,
            String field,
            BindingResult errors,
            String requiredMessage,
            String formatMessage,
            Function<String, T> parser) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            errors.rejectValue(field, field + ".required", requiredMessage);
            return null;
        }
        try {
            return parser.apply(text);
        } catch (IllegalArgumentException _) {
            errors.rejectValue(field, field + ".format", formatMessage);
            return null;
        }
    }

    /** 受付時刻（日本時間）。空は「今」（空の Optional）。形式の誤りと、今より後の時刻は誤り。 */
    private static Optional<UtcInstant> receivedAt(String value, UtcInstant now, BindingResult errors) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            UtcInstant parsed = new UtcInstant(LocalDateTime.parse(text, RECEIVED_AT_FORMAT)
                    .atZone(DateTimeDisplay.ZONE)
                    .toInstant());
            if (parsed.instant().isAfter(now.instant())) {
                errors.rejectValue("receivedAt", "receivedAt.future", "今より後の時刻は入れられません");
                return Optional.empty();
            }
            return Optional.of(parsed);
        } catch (DateTimeParseException _) {
            errors.rejectValue("receivedAt", "receivedAt.format", "2026-11-16 10:30 の形（日本時間）で入力してください");
            return Optional.empty();
        }
    }

    /** 長さの上限だけを確かめる任意の入力（空は空の文字列）。 */
    private static String bounded(String value, String field, int limit, BindingResult errors) {
        String text = value == null ? "" : value.strip();
        if (text.length() > limit) {
            errors.rejectValue(field, field + ".length", limit + " 文字までで入力してください");
            return "";
        }
        return text;
    }

    private static String department(String value, BindingResult errors) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return DEFAULT_DEPARTMENT;
        }
        if (text.length() > DEPARTMENT_LIMIT) {
            errors.rejectValue("department", "department.length", DEPARTMENT_LIMIT + " 文字までで入力してください");
            return DEFAULT_DEPARTMENT;
        }
        return text;
    }
}
