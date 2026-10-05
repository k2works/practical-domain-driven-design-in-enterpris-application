package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 見積依頼の画面（荷主の C-02・C-03・C-04、社内の S-02・S-03）で共通に使う表示名と日時の書式。
 */
final class TransportRequestLabels {

    /** 画面のタイムゾーン。利用者ごとの設定は US-18 以降で入れる（BR-10）。 */
    static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Tokyo");

    /** 日時表示の共通部品（UI 設計）: 「年月日 時刻 タイムゾーン（UTC offset）」。 */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV（'UTC'xxx）");

    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    static final List<Option> CARGO_CATEGORIES = List.of(
            new Option(CargoCategory.GENERAL.name(), "一般"),
            new Option(CargoCategory.DANGEROUS.name(), "危険物"),
            new Option(CargoCategory.REEFER.name(), "冷凍"),
            new Option(CargoCategory.OTHER_SPECIAL.name(), "その他特殊"));

    static final List<Option> PACKAGE_TYPES = List.of(
            new Option(PackageType.PALLET.name(), "パレット"),
            new Option(PackageType.CARTON.name(), "カートン"),
            new Option(PackageType.CRATE.name(), "クレート"),
            new Option(PackageType.OTHER.name(), "その他"));

    private TransportRequestLabels() {}

    /** 荷主の画面の日時（例: 2026-10-05 10:00 Asia/Tokyo（UTC+09:00））。 */
    static String customerDateTime(UtcInstant instant) {
        return DATE_TIME.format(ZonedDateTime.ofInstant(instant.instant(), DISPLAY_ZONE));
    }

    /** 社内の画面の日時。利用者のタイムゾーンを主にし、UTC を括弧で併記する（UI 設計の共通部品「日時表示」）。 */
    static String staffDateTime(UtcInstant instant) {
        return customerDateTime(instant) + "（UTC "
                + UTC_TIME.format(instant.instant().atOffset(ZoneOffset.UTC)) + "）";
    }

    /**
     * 荷主の画面の状態の表示名。いま誰の対応待ちかを添える（UI 設計 C-04 の対応表。Bolt 6）。
     * 社内の画面の表示名（{@link #status(TransportRequestStatus)}）は変えない。
     */
    static String customerStatus(TransportRequestStatus status) {
        return switch (status) {
            case DRAFT -> "差戻し（お客様の対応待ち）";
            case UNDER_REVIEW -> "審査中（A 社の対応待ち）";
            case QUOTING -> "見積り作成中（A 社の対応待ち）";
        };
    }

    /** 荷主の画面で、いま誰が何をしているか、荷主が次に何をするかの案内。 */
    static String customerGuidance(TransportRequestStatus status) {
        return switch (status) {
            case DRAFT -> "営業担当者が見積依頼を差し戻しました。差戻しの理由を確かめ、直して出し直してください。";
            case UNDER_REVIEW -> "営業担当者が内容を審査しています。お問い合わせの際は業務番号をお伝えください。";
            case QUOTING -> "営業担当者が見積りを作成しています。お問い合わせの際は業務番号をお伝えください。";
        };
    }

    static String status(TransportRequestStatus status) {
        return switch (status) {
            case DRAFT -> "差戻し（下書き）";
            case UNDER_REVIEW -> "審査中";
            case QUOTING -> "見積り作成中";
        };
    }

    /** 業務番号と版の表記（例: TR-2026-0001 版 1）。電話やメールでそのまま伝えられる形にする（D-4）。 */
    static String numberWithVersion(TransportRequestNumber number, int versionNo) {
        return number.text() + " 版 " + versionNo;
    }

    static String decision(ReviewDecision decision) {
        return switch (decision) {
            case APPROVED -> "充足（審査を確定）";
            case SENT_BACK -> "差戻し";
        };
    }

    /** 待っている時間（例: 3 時間 20 分、2 日 4 時間）。受付一覧で、待たせている長さを示す。 */
    static String elapsed(Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        long days = minutes / (24 * 60);
        long hours = minutes % (24 * 60) / 60;
        long restMinutes = minutes % 60;
        if (days > 0) {
            return days + " 日 " + hours + " 時間";
        }
        if (hours > 0) {
            return hours + " 時間 " + restMinutes + " 分";
        }
        return restMinutes + " 分";
    }

    static String cargoCategory(CargoCategory category) {
        return label(CARGO_CATEGORIES, category.name());
    }

    static String packageType(PackageType packageType) {
        return label(PACKAGE_TYPES, packageType.name());
    }

    private static String label(List<Option> options, String value) {
        return options.stream()
                .filter(option -> option.value().equals(value))
                .map(Option::label)
                .findFirst()
                .orElseThrow();
    }

    /**
     * 選択の欄の 1 つの選択肢。
     *
     * @param value 送る値（ドメインの定数名）
     * @param label 画面に出す名前
     */
    public record Option(String value, String label) {}

    /** 不足事項の表示。なければ（空・空白だけを含む）「（なし）」とする（Bolt 6〜8 レビュー R-32）。 */
    static String missingItems(String missingItems) {
        return missingItems == null || missingItems.isBlank() ? "（なし）" : missingItems;
    }
}
