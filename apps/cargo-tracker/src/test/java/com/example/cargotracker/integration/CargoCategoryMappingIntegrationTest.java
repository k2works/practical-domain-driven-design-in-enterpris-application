package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.interfaces.api.RouteConditionView;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteConditions;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteCargoCategory;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 契約テスト。見積りの貨物種別のすべての名前が、経路設計の貨物種別に翻訳され、`UNRECOGNIZED` に写らない
 * （D-65、Bolt 39i の行 17）。見積りに値が増えて経路設計の翻訳が追いつかなければ落ちる。
 */
class CargoCategoryMappingIntegrationTest {

    @ParameterizedTest(name = "見積りの {0} は UNRECOGNIZED に写らない")
    @EnumSource(CargoCategory.class)
    void 見積りの貨物種別の名前はすべて経路設計の種別に写る(CargoCategory quotationCategory) {
        QuotationRouteConditions conditions =
                new QuotationRouteConditions((id, versionNo) -> Optional.of(new RouteConditionView(
                        "TR-2026-0001",
                        new Location("JPTYO"),
                        new Location("NLRTM"),
                        new UtcInstant(Instant.parse("2026-11-02T00:00:00Z")),
                        quotationCategory.name(),
                        new CompanyId(UUID.randomUUID()))));

        RouteCargoCategory translated = conditions
                .find(UUID.randomUUID(), 1)
                .orElseThrow()
                .specification()
                .cargoCategory();

        assertThat(translated).isNotEqualTo(RouteCargoCategory.UNRECOGNIZED);
        assertThat(translated.name()).isEqualTo(quotationCategory.name());
    }
}
