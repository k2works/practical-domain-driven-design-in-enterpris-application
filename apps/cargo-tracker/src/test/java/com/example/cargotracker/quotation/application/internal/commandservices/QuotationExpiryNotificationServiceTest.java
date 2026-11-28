package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.domain.events.QuotationExpiring;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.Currency;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingLineInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 見積りの有効期限の 24 時間前の通知の検出（US-22 AC2。Bolt 43-2）。承認待ち（提示済み・荷主承認待ち）の見積りのうち、有効期限が検出の時刻の 23 時間
 * 後より後で 24 時間後以前のものだけを、荷主企業と業務番号つきの {@code QuotationExpiring} として発行する（1 時間ごとの検出で、1 つの見積りが
 * ちょうど 1 回の窓に入る）。同じ窓を二重に検出しても、宛先の側の一意制約が 2 通を防ぐ。
 */
class QuotationExpiryNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");
    private static final CompanyId COMPANY = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000043c0001"));
    private static final UserId STAFF = new UserId(UUID.randomUUID());

    private final InMemoryQuotationRepository quotations = new InMemoryQuotationRepository();
    private final InMemoryTransportRequestRepository requests = new InMemoryTransportRequestRepository();
    private final List<Object> published = new ArrayList<>();
    private final QuotationExpiryNotificationService service = new QuotationExpiryNotificationService(
            quotations, requests, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

    private TransportRequest request(int sequence) {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, sequence),
                COMPANY,
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                new UtcInstant(NOW.minusSeconds(3600)));
        requests.save(request);
        return requests.findById(request.id()).orElseThrow();
    }

    /** 提示済みで、有効期限が指定の見積りを保存する。 */
    private Quotation presented(TransportRequest request, Instant expiresAt) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        quotation.calculate(
                new QuotationInput(
                        List.of(new PricingLineInput("海上運賃", new BigDecimal("3200.00"), "契約")),
                        Currency.USD,
                        new UtcInstant(expiresAt),
                        List.of(new Location("SGSIN")),
                        new UtcInstant(Instant.parse("2099-10-10T00:00:00Z")),
                        new UtcInstant(Instant.parse("2099-10-30T09:00:00Z"))),
                new UtcInstant(NOW.minusSeconds(1800)));
        quotation.presentInternally(STAFF, new UtcInstant(NOW.minusSeconds(900)));
        quotation.clearDomainEvents();
        quotations.save(quotation);
        return quotation;
    }

    @Test
    void 行1_有効期限が24時間以内の窓にある承認待ちの見積りだけを荷主企業と業務番号つきで発行する() {
        TransportRequest inWindow = request(1);
        presented(inWindow, NOW.plus(Duration.ofHours(23)).plusSeconds(1));
        TransportRequest atEdge = request(2);
        presented(atEdge, NOW.plus(Duration.ofHours(24)));

        int detected = service.detect();

        assertThat(detected).isEqualTo(2);
        assertThat(published)
                .allSatisfy(event -> assertThat(event).isInstanceOf(QuotationExpiring.class))
                .extracting(event -> ((QuotationExpiring) event).transportRequestNumber())
                .containsExactlyInAnyOrder("TR-2026-0001", "TR-2026-0002");
        assertThat(((QuotationExpiring) published.getFirst()).shipperCompanyId())
                .isEqualTo(COMPANY.value());
    }

    @Test
    void 行2_窓の外は発行しない_23時間ちょうどと24時間より後() {
        presented(request(1), NOW.plus(Duration.ofHours(23)));
        presented(request(2), NOW.plus(Duration.ofHours(24)).plusSeconds(1));
        presented(request(3), NOW.plus(Duration.ofHours(2)));

        assertThat(service.detect()).isZero();
        assertThat(published).isEmpty();
    }

    @Test
    void 行3_輸送要求が読めない見積りは発行しない() {
        Quotation orphan =
                Quotation.create(new QuotationId(UUID.randomUUID()), new TransportRequestId(UUID.randomUUID()), 1, 1);
        orphan.calculate(
                new QuotationInput(
                        List.of(new PricingLineInput("海上運賃", new BigDecimal("3200.00"), "契約")),
                        Currency.USD,
                        new UtcInstant(NOW.plus(Duration.ofHours(23)).plusSeconds(60)),
                        List.of(new Location("SGSIN")),
                        new UtcInstant(Instant.parse("2099-10-10T00:00:00Z")),
                        new UtcInstant(Instant.parse("2099-10-30T09:00:00Z"))),
                new UtcInstant(NOW.minusSeconds(1800)));
        orphan.presentInternally(STAFF, new UtcInstant(NOW.minusSeconds(900)));
        orphan.clearDomainEvents();
        quotations.save(orphan);

        assertThat(service.detect()).isZero();
        assertThat(published).isEmpty();
    }
}
