package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 見積りの集約（作成中 → 承認待ち → 提示済み。Q-INV-05・17。Bolt 10）。 */
class QuotationTest {

    private final QuotationId id = new QuotationId(UUID.randomUUID());
    private final TransportRequestId transportRequestId = new TransportRequestId(UUID.randomUUID());
    private final UserId approver = new UserId(UUID.randomUUID());
    private final UtcInstant now = new UtcInstant(Instant.parse("2026-10-05T04:00:00Z"));

    private Quotation created() {
        return Quotation.create(id, transportRequestId, 1, 1);
    }

    @Test
    void 作った見積りは作成中で料金根拠も提示時刻もない() {
        Quotation quotation = created();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(quotation.pricingBasis()).isEmpty();
        assertThat(quotation.presentedAt()).isEmpty();
    }

    @Test
    void 算出すると承認待ちになり料金根拠と有効期限と経路方針を持つ() {
        Quotation quotation = created();

        assertThat(quotation.calculate(QuotationFixture.completeInput(), now)).isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
        assertThat(quotation.pricingBasis())
                .hasValueSatisfying(basis -> assertThat(basis.total()).isEqualByComparingTo("3730.00"));
        assertThat(quotation.expiry())
                .hasValueSatisfying(expiry -> assertThat(expiry.expiresAt()).isEqualTo(QuotationFixture.EXPIRES_AT));
        assertThat(quotation.routePolicy())
                .hasValueSatisfying(policy -> assertThat(policy.via()).containsExactly(new Location("SGSIN")));
    }

    @Test
    void 誤りのある入力では算出せず作成中のまま違反を返す() {
        Quotation quotation = created();

        assertThat(quotation.calculate(new QuotationInput(List.of(), null, null, List.of(), null, null), now))
                .hasValueSatisfying(
                        violations -> assertThat(violations.isEmpty()).isFalse());
        assertThat(quotation.status()).isEqualTo(QuotationStatus.DRAFT);
    }

    @Test
    void 承認待ちでない見積りは算出し直せない() {
        Quotation quotation = created();
        QuotationInput input = QuotationFixture.completeInput();
        quotation.calculate(input, now);

        assertThatThrownBy(() -> quotation.calculate(input, now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 社内承認して提示すると提示済みになり承認者と提示時刻を残しDE03を生成する() {
        Quotation quotation = created();
        quotation.calculate(QuotationFixture.completeInput(), now);

        assertThat(quotation.presentInternally(approver, now)).isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.PRESENTED);
        assertThat(quotation.approvedBy()).contains(approver);
        assertThat(quotation.presentedAt()).contains(now);
        assertThat(quotation.domainEvents()).singleElement().isInstanceOfSatisfying(QuotationPresented.class, event -> {
            assertThat(event.quotationId()).isEqualTo(id.value());
            assertThat(event.quotationNo()).isEqualTo(1);
            assertThat(event.transportRequestId()).isEqualTo(transportRequestId.value());
            assertThat(event.transportRequestVersionNo()).isEqualTo(1);
            assertThat(event.expiresAt()).isEqualTo(QuotationFixture.EXPIRES_AT);
            assertThat(event.routeVia()).containsExactly("SGSIN");
            assertThat(event.presentedAt()).isEqualTo(now);
        });
    }

    @Test
    void 承認待ちでない見積りは提示できない() {
        Quotation draft = created();

        assertThat(draft.presentInternally(approver, now)).contains(QuotationRejection.NOT_PENDING_APPROVAL);
        assertThat(draft.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(draft.domainEvents()).isEmpty();
    }
}
