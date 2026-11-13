package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * 有人案件の入力の変換（Bolt 40-12）。次回更新日時を日本時間の {@code yyyy-MM-dd HH:mm} から UTC の時刻にする（日時の変換は層の規則で
 * このクラスに置く）。
 */
final class ServiceCaseFormConverter {

    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter NEXT_UPDATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private ServiceCaseFormConverter() {}

    /** 日本時間の日時を UTC の時刻にする。空と形式の誤りは空（誤りかどうかは呼び手が入力の空と合わせて判断する）。 */
    static Optional<UtcInstant> nextUpdate(String text) {
        try {
            return Optional.of(new UtcInstant(LocalDateTime.parse(text.strip(), NEXT_UPDATE_FORMAT)
                    .atZone(JAPAN)
                    .toInstant()));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }
}
