package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipmentTermsInputTest {

    private static final UtcInstant SUBMITTED_AT = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));
    private static final CompanyId CONSIGNEE = new CompanyId(UUID.randomUUID());

    private final MvpAcceptancePolicy policy = new MvpAcceptancePolicy();

    private final ShipmentTermsInput complete = new ShipmentTermsInput(
            CONSIGNEE,
            new Location("JPTYO"),
            new Location("NLRTM"),
            new UtcInstant(Instant.parse("2026-11-02T00:00:00Z")),
            CargoCategory.GENERAL,
            PackageType.PALLET,
            12,
            new BigDecimal("8400"),
            new BigDecimal("32.5"));

    @Test
    void 必須条件がそろっていれば違反はなく輸送条件になる() {
        ShipmentTerms terms = valid(complete);

        assertThat(terms.consigneeCompanyId()).isEqualTo(CONSIGNEE);
        assertThat(terms.origin()).isEqualTo(new Location("JPTYO"));
        assertThat(terms.destination()).isEqualTo(new Location("NLRTM"));
        assertThat(terms.arrivalDeadline()).isEqualTo(new UtcInstant(Instant.parse("2026-11-02T00:00:00Z")));
        assertThat(terms.cargo())
                .isEqualTo(new Cargo(
                        CargoCategory.GENERAL, PackageType.PALLET, 12, new BigDecimal("8400"), new BigDecimal("32.5")));
    }

    @Test
    void 不足と誤りをまとめて返す() {
        ShipmentTermsInput input = new ShipmentTermsInput(
                null, new Location("JPTYO"), new Location("JPTYO"), null, CargoCategory.REEFER, null, 0, null, null);

        assertThat(violations(input))
                .containsExactlyInAnyOrder(
                        new Violation(Item.CONSIGNEE, Reason.MISSING),
                        new Violation(Item.DESTINATION, Reason.SAME_AS_ORIGIN),
                        new Violation(Item.ARRIVAL_DEADLINE, Reason.MISSING),
                        new Violation(Item.CARGO_CATEGORY, Reason.OUTSIDE_MVP),
                        new Violation(Item.PACKAGE_TYPE, Reason.MISSING),
                        new Violation(Item.PACKAGE_COUNT, Reason.NOT_POSITIVE),
                        new Violation(Item.GROSS_WEIGHT_KG, Reason.MISSING),
                        new Violation(Item.VOLUME_M3, Reason.MISSING));
    }

    @Test
    void すべて空なら全項目の不足を返す() {
        ShipmentTermsInput empty = new ShipmentTermsInput(null, null, null, null, null, null, null, null, null);

        assertThat(violations(empty)).extracting(Violation::item).containsExactlyInAnyOrder(Item.values());
    }

    @Test
    void 希望到着期限が提出時刻と同時刻なら誤りで1マイクロ秒後なら受け付ける() {
        assertThat(violations(withDeadline("2026-10-05T01:00:00Z")))
                .containsExactly(new Violation(Item.ARRIVAL_DEADLINE, Reason.NOT_AFTER_SUBMISSION));
        assertThat(valid(withDeadline("2026-10-05T01:00:00.000001Z")).arrivalDeadline())
                .isEqualTo(new UtcInstant(Instant.parse("2026-10-05T01:00:00.000001Z")));
    }

    @Test
    void 総重量と容積の小数点以下が3桁を超えれば誤り() {
        ShipmentTermsInput input = new ShipmentTermsInput(
                CONSIGNEE,
                new Location("JPTYO"),
                new Location("NLRTM"),
                new UtcInstant(Instant.parse("2026-11-02T00:00:00Z")),
                CargoCategory.GENERAL,
                PackageType.PALLET,
                12,
                new BigDecimal("8400.1234"),
                new BigDecimal("32.5001"));

        assertThat(violations(input))
                .containsExactlyInAnyOrder(
                        new Violation(Item.GROSS_WEIGHT_KG, Reason.TOO_MANY_DECIMALS),
                        new Violation(Item.VOLUME_M3, Reason.TOO_MANY_DECIMALS));
    }

    @Test
    void 末尾のゼロで桁が増えても小数点以下3桁の誤りにしない() {
        ShipmentTermsInput input = new ShipmentTermsInput(
                CONSIGNEE,
                new Location("JPTYO"),
                new Location("NLRTM"),
                new UtcInstant(Instant.parse("2026-11-02T00:00:00Z")),
                CargoCategory.GENERAL,
                PackageType.PALLET,
                12,
                new BigDecimal("8400.0000"),
                new BigDecimal("32.50000"));

        assertThat(valid(input).cargo().grossWeightKg()).isEqualByComparingTo("8400");
    }

    @Test
    void 違反があれば輸送条件は得られず違反だけを返す() {
        ShipmentTermsInput empty = new ShipmentTermsInput(null, null, null, null, null, null, null, null, null);

        assertThat(empty.validate(SUBMITTED_AT, policy)).isInstanceOf(ShipmentTermsInput.Invalid.class);
    }

    private ShipmentTerms valid(ShipmentTermsInput input) {
        return ((ShipmentTermsInput.Valid) input.validate(SUBMITTED_AT, policy)).terms();
    }

    private List<Violation> violations(ShipmentTermsInput input) {
        return ((ShipmentTermsInput.Invalid) input.validate(SUBMITTED_AT, policy))
                .violations()
                .violations();
    }

    private ShipmentTermsInput withDeadline(String deadline) {
        return new ShipmentTermsInput(
                CONSIGNEE,
                new Location("JPTYO"),
                new Location("NLRTM"),
                new UtcInstant(Instant.parse(deadline)),
                CargoCategory.GENERAL,
                PackageType.PALLET,
                12,
                new BigDecimal("8400"),
                new BigDecimal("32.5"));
    }
}
