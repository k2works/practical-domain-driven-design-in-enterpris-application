package com.example.cargotracker.quotation.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CargoDisplayNamesTest {

    @ParameterizedTest
    @CsvSource({"GENERAL, 一般", "DANGEROUS, 危険物", "REEFER, 冷凍", "OTHER_SPECIAL, その他特殊"})
    void 貨物種別は用語集の日本語名で示す(CargoCategory category, String name) {
        assertThat(CargoDisplayNames.category(category)).isEqualTo(name);
    }

    @ParameterizedTest
    @CsvSource({"PALLET, パレット", "CARTON, カートン", "CRATE, クレート", "OTHER, その他"})
    void 荷姿は用語集の日本語名で示す(PackageType packageType, String name) {
        assertThat(CargoDisplayNames.packageType(packageType)).isEqualTo(name);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "8400.000 | 8,400",
                "32.500 | 32.5",
                "1200.500 | 1,200.5",
                "0.250 | 0.25",
                "123456789.000 | 123,456,789",
                "0.001 | 0.001",
                "1000 | 1,000",
                "0.0005 | 0.001"
            })
    void 重量と容積は不要な0を落とし整数部に桁区切りを入れ指数の表記にしない(BigDecimal value, String shown) {
        assertThat(CargoDisplayNames.quantity(value)).isEqualTo(shown);
    }

    @Test
    void 予約へ渡す貨物の要約は日本語名と表示の書式で作る() {
        Cargo cargo = new Cargo(
                CargoCategory.GENERAL, PackageType.PALLET, 12, new BigDecimal("8400.000"), new BigDecimal("32.500"));

        assertThat(CargoDisplayNames.summary(cargo)).isEqualTo("一般 / パレット × 12 / 8,400 kg / 32.5 m3");
    }
}
