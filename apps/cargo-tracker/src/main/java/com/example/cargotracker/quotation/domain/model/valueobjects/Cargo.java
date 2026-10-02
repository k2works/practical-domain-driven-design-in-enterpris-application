package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * 貨物。貨物種別、荷姿、個数、総重量（kg）、容積（m3）の組。
 * 個数は 1 以上、総重量と容積は 0 より大きく小数点以下 3 桁まで（Q-INV-12）。
 * 総重量と容積は小数点以下 3 桁にそろえて持つ（保存する列の桁と同じにし、比べたときに桁の違いで食い違わないようにする）。
 *
 * @param category 貨物種別
 * @param packageType 荷姿
 * @param packageCount 個数
 * @param grossWeightKg 総重量（kg）
 * @param volumeM3 容積（m3）
 */
@ValueObject
public record Cargo(
        CargoCategory category,
        PackageType packageType,
        int packageCount,
        BigDecimal grossWeightKg,
        BigDecimal volumeM3) {

    /** 総重量と容積の小数点以下の桁数の上限。 */
    public static final int MAX_DECIMAL_PLACES = 3;

    public Cargo {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(packageType, "packageType");
        if (packageCount < 1) {
            throw new IllegalArgumentException("個数は 1 以上です: " + packageCount);
        }
        grossWeightKg = normalize(grossWeightKg, "grossWeightKg");
        volumeM3 = normalize(volumeM3, "volumeM3");
    }

    /** 0 より大きいか。 */
    static boolean isPositive(BigDecimal value) {
        return value.signum() > 0;
    }

    /** 小数点以下が上限の桁数に収まるか。末尾のゼロは桁に数えない。 */
    static boolean fitsDecimalPlaces(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= MAX_DECIMAL_PLACES;
    }

    private static BigDecimal normalize(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (!isPositive(value) || !fitsDecimalPlaces(value)) {
            throw new IllegalArgumentException(name + " は 0 より大きく小数点以下 3 桁までです: " + value);
        }
        return value.setScale(MAX_DECIMAL_PLACES);
    }
}
