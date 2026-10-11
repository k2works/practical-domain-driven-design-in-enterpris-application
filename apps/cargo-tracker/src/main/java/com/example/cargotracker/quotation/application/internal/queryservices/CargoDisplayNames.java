package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * 貨物種別・荷姿の日本語名（用語集の名前）と、重量・容積の表示の書式（Bolt 29c、#46）。
 * 予約へ渡す貨物の要約が公開 API の値で、画面の層の外で作るため、見積りの application 層に置く。見積りの画面のラベルと条件の表も
 * ここを使い、名前と書式を 1 か所にする。ドメインの enum は表示の関心で変えない（Bolt 22 の D-84 と同じく、共有カーネルとドメインの型を
 * 表示のために変えない）。
 */
public final class CargoDisplayNames {

    private CargoDisplayNames() {}

    /** 貨物種別の日本語名。 */
    public static String category(CargoCategory category) {
        return switch (category) {
            case GENERAL -> "一般";
            case DANGEROUS -> "危険物";
            case REEFER -> "冷凍";
            case OTHER_SPECIAL -> "その他特殊";
        };
    }

    /** 荷姿の日本語名。 */
    public static String packageType(PackageType packageType) {
        return switch (packageType) {
            case PALLET -> "パレット";
            case CARTON -> "カートン";
            case CRATE -> "クレート";
            case OTHER -> "その他";
        };
    }

    /**
     * 重量・容積の表示。不要な 0 を落とし、整数部に 3 桁ごとのカンマを入れる（例: 8400.000 → 8,400、32.500 → 32.5）。小数は貨物の値と同じく
     * 3 桁まで（4 桁目は四捨五入。貨物の値は小数 3 桁までに正規化されている前提）。{@code BigDecimal.stripTrailingZeros} の指数の表記
     * （8.4E+3）にならないよう、書式で組み立てる。
     */
    public static String quantity(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setMaximumFractionDigits(Cargo.MAX_DECIMAL_PLACES);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    /** 予約へ渡す貨物の要約（表示用の文字列。予約は解釈しない）。例: 「一般 / パレット × 12 / 8,400 kg / 32.5 m3」。 */
    public static String summary(Cargo cargo) {
        return category(cargo.category()) + " / " + packageType(cargo.packageType()) + " × " + cargo.packageCount()
                + " / " + quantity(cargo.grossWeightKg()) + " kg / " + quantity(cargo.volumeM3()) + " m3";
    }
}
