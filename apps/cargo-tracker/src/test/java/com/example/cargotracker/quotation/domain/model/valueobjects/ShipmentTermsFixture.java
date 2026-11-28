package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * テストで使う輸送条件の見本。必須条件がそろった一般貨物の、東京からロッテルダムへの依頼。
 */
public final class ShipmentTermsFixture {

    public static final CompanyId CONSIGNEE = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000201"));
    /**
     * 希望到着期限。実際の時計で動くテスト（H2 のスモーク、配信の統合テスト）でも提出時刻より後になるよう、十分に先にする（Bolt 4 レビュー R-01）。
     * 期限の境界は、固定の時計で動くテストがそれぞれ自分の期限で確かめる。
     */
    public static final UtcInstant ARRIVAL_DEADLINE = new UtcInstant(Instant.parse("2099-11-02T00:00:00Z"));

    private ShipmentTermsFixture() {}

    /** 必須条件がそろった輸送条件の入力。 */
    public static ShipmentTermsInput completeInput() {
        return new ShipmentTermsInput(
                CONSIGNEE,
                new Location("JPTYO"),
                new Location("NLRTM"),
                ARRIVAL_DEADLINE,
                CargoCategory.GENERAL,
                PackageType.PALLET,
                12,
                new BigDecimal("8400"),
                new BigDecimal("32.5"));
    }

    /** 必須条件がそろった輸送条件。 */
    public static ShipmentTerms generalCargo() {
        return new ShipmentTerms(
                CONSIGNEE,
                new Location("JPTYO"),
                new Location("NLRTM"),
                ARRIVAL_DEADLINE,
                new Cargo(
                        CargoCategory.GENERAL, PackageType.PALLET, 12, new BigDecimal("8400"), new BigDecimal("32.5")));
    }
}
