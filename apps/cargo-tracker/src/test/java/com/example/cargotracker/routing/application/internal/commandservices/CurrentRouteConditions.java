package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.quotation.interfaces.api.RouteConditionView;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteConditions;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * どの案件の版も現在の版だと答える見積りの公開 API の代役。経路設計の単体・永続化のテストは輸送要求を持たないので使う（Bolt 62）。
 */
public final class CurrentRouteConditions {

    private CurrentRouteConditions() {}

    public static QuotationRouteConditions always() {
        RouteConditionView view = new RouteConditionView(
                "TR-2026-0001",
                new Location("JPTYO"),
                new Location("NLRTM"),
                new UtcInstant(Instant.parse("2026-11-02T00:00:00Z")),
                "GENERAL",
                new CompanyId(UUID.randomUUID()));
        return new QuotationRouteConditions((transportRequestId, versionNo) -> Optional.of(view));
    }
}
