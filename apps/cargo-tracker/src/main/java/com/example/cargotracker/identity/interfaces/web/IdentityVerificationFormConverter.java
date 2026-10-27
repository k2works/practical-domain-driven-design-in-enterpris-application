package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * S-20 の認証アプリの登録の解除の確認の、本人確認の日時の欄の解釈（Bolt 38c の開始準備の決定 (11)）。S-13 と同じ日本時間の 1 つのテキスト
 * の欄（例: 2026-11-09 10:30）を厳格に解釈し、UTC の時点にする（タイムゾーンは日時表示の共通部品の {@link DateTimeDisplay#ZONE}）。
 * 画面の層の日時の規則（{@code LayerArchitectureTest}。`DateTimeFormatter` に依存してよいのは名前が {@code FormConverter} で終わる部品
 * だけ）に合わせて名前を付ける。範囲（未来・7 日より前）はドメインの規則が判定する。
 */
final class IdentityVerificationFormConverter {

    /** 欄の形。 */
    static final String PATTERN = "uuuu-MM-dd HH:mm";

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern(PATTERN).withResolverStyle(ResolverStyle.STRICT);

    private IdentityVerificationFormConverter() {}

    /**
     * 入れた日時を UTC の時点にする。前後の空白を除く。形の誤りと存在しない日時は空（空欄は呼ぶ側が先に分ける）。
     *
     * @param text 入れたままの日時
     */
    static Optional<UtcInstant> verifiedAt(String text) {
        try {
            return Optional.of(new UtcInstant(LocalDateTime.parse(text.strip(), FORMAT)
                    .atZone(DateTimeDisplay.ZONE)
                    .toInstant()));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }

    /**
     * 確認の画面が hidden で持つ登録の時刻（ISO-8601 の UTC）を時点にする。ない・形の誤りは空。
     *
     * @param value 送られた値
     */
    static Optional<UtcInstant> enrolledAt(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new UtcInstant(Instant.parse(value)));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }
}
