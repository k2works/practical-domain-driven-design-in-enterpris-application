package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
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
 * S-13 主要実績の登録の入力を、登録のコマンドに変換する（Bolt 26c）。ここでは形式（選択肢・UN/LOCODE・日時・参照の長さ）だけを確かめ、
 * 業務の規則（同じ出典・未来の発生時刻）はドメインが判定する。形式の誤りはすべて BindingResult に記録してから返す（1 件ずつ直させない）。
 *
 * <p>日時と場所の扱いは見積依頼の入力の変換（{@code quotation} の {@code TransportRequestFormConverter}）と同じ。日時の入力の部品を
 * {@code platform.web} に集めるのは 3 か所目で行う（T-63）。
 */
final class MilestoneFormConverter {

    static final String OCCURRED_AT_PATTERN = "uuuu-MM-dd HH:mm";

    /** 画面で選べる出典の種類。外部原本は外部原本の取込（W7）だけが作る（Bolt 26c 計画の確認ポイント 8）。 */
    static final List<SourceKind> SELECTABLE_SOURCE_KINDS =
            List.of(SourceKind.FIELD_RECORD, SourceKind.INTERNAL_CHECK, SourceKind.MANUAL_ENTRY);

    static final String CHOICE_MESSAGE = "選択肢から選んでください";

    /** 必須の誤り。文言は項目の名前で始めない（エラー要約が項目の名前を前に付ける。共通部品「エラー要約」）。 */
    static final String REQUIRED_MESSAGE = "入力してください";

    private static final DateTimeFormatter OCCURRED_AT_FORMAT =
            DateTimeFormatter.ofPattern(OCCURRED_AT_PATTERN).withResolverStyle(ResolverStyle.STRICT);

    private MilestoneFormConverter() {}

    /**
     * 入力を変換する。形式の誤りがあれば BindingResult に記録して空を返す。
     *
     * @param registeredAt 登録時刻（出典の取得時刻にする。Bolt 26c 計画の確認ポイント 7）
     */
    static Optional<RegisterMilestoneCommand> convert(
            MilestoneForm form,
            TrackingNumber trackingNumber,
            UserId registrant,
            UtcInstant registeredAt,
            BindingResult errors) {
        MilestoneKind kind =
                required(form.getKind(), "kind", errors, CHOICE_MESSAGE, CHOICE_MESSAGE, MilestoneKind::valueOf);
        Location location = required(
                form.getLocation(),
                "location",
                errors,
                REQUIRED_MESSAGE,
                "UN/LOCODE（国コード 2 文字 + 地点コード 3 文字、例: JPTYO）で入力してください",
                // 貼り付けで付く前後の空白を除き、小文字は大文字にそろえる（見積依頼と同じ）
                text -> new Location(text.toUpperCase(Locale.ROOT)));
        UtcInstant occurredAt = required(
                form.getOccurredAt(),
                "occurredAt",
                errors,
                REQUIRED_MESSAGE,
                "2026-11-01 11:30 の形（日本時間）で入力してください",
                MilestoneFormConverter::occurredAt);
        SourceKind sourceKind = required(
                form.getSourceKind(),
                "sourceKind",
                errors,
                CHOICE_MESSAGE,
                CHOICE_MESSAGE,
                MilestoneFormConverter::selectable);
        String reference = reference(form.getSourceReference(), errors);
        if (errors.hasErrors()) {
            return Optional.empty();
        }
        return Optional.of(new RegisterMilestoneCommand(
                trackingNumber,
                form.getExpectedVersion(),
                kind,
                location,
                occurredAt,
                new Source(sourceKind, reference, registeredAt),
                registrant));
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
            // 形式の誤り（選択肢の値・UN/LOCODE・日時）だけを入力の誤りにする。それ以外の例外は実装の不具合なのでそのまま投げる
            errors.rejectValue(field, field + ".format", formatMessage);
            return null;
        }
    }

    /** 出典の参照は前後の空白を除く。大文字と小文字は区別したまま（外部の識別子。Bolt 26b の P-4）。 */
    private static String reference(String value, BindingResult errors) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            errors.rejectValue("sourceReference", "sourceReference.required", REQUIRED_MESSAGE);
            return null;
        }
        if (text.length() > Source.REFERENCE_MAX_LENGTH) {
            errors.rejectValue(
                    "sourceReference", "sourceReference.length", Source.REFERENCE_MAX_LENGTH + " 文字までで入力してください");
            return null;
        }
        return text;
    }

    private static SourceKind selectable(String text) {
        SourceKind sourceKind = SourceKind.valueOf(text);
        if (!SELECTABLE_SOURCE_KINDS.contains(sourceKind)) {
            throw new IllegalArgumentException("画面で選べない出典の種類: " + text);
        }
        return sourceKind;
    }

    private static UtcInstant occurredAt(String text) {
        try {
            return new UtcInstant(LocalDateTime.parse(text, OCCURRED_AT_FORMAT)
                    .atZone(DateTimeDisplay.ZONE)
                    .toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
