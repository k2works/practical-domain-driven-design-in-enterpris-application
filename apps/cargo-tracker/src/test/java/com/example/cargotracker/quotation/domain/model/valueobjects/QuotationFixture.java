package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** テストで使う見積りの入力の見本。海上運賃と燃料調整金の 2 行、USD、シンガポール経由。 */
public final class QuotationFixture {

    public static final UtcInstant EXPIRES_AT = new UtcInstant(Instant.parse("2099-10-08T09:00:00Z"));

    private QuotationFixture() {}

    /** そろった見積りの入力。有効期限は実際の時計で動くテストでも算出の時刻より後になるよう十分に先にする。 */
    public static QuotationInput completeInput() {
        return new QuotationInput(
                List.of(
                        new PricingLineInput("海上運賃", new BigDecimal("3200.00"), "年間契約 2026-A"),
                        new PricingLineInput("燃料調整金", new BigDecimal("530.00"), null)),
                Currency.USD,
                EXPIRES_AT,
                List.of(new Location("SGSIN")),
                new UtcInstant(Instant.parse("2099-10-10T00:00:00Z")),
                new UtcInstant(Instant.parse("2099-10-30T09:00:00Z")));
    }

    /** 割り当てた経路の見本（RC-2026-0001 経路版 1。東京 → シンガポール → ロッテルダム。Bolt 20）。 */
    public static AssignedRoute assignedRoute() {
        return new AssignedRoute(
                "RC-2026-0001",
                1,
                new UtcInstant(Instant.parse("2026-10-07T05:00:00Z")),
                List.of(
                        new AssignedRouteLeg(
                                "V-201",
                                new Location("JPTYO"),
                                new Location("SGSIN"),
                                new UtcInstant(Instant.parse("2099-10-10T00:00:00Z")),
                                new UtcInstant(Instant.parse("2099-10-20T00:00:00Z"))),
                        new AssignedRouteLeg(
                                "V-301",
                                new Location("SGSIN"),
                                new Location("NLRTM"),
                                new UtcInstant(Instant.parse("2099-10-20T12:00:00Z")),
                                new UtcInstant(Instant.parse("2099-10-31T00:00:00Z")))));
    }
}
