package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.aggregates.DuplicateQuotationException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuotationCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T04:00:00Z");
    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final UserId STAFF = new UserId(UUID.randomUUID());

    private final InMemoryTransportRequestRepository transportRequests = new InMemoryTransportRequestRepository();
    private final InMemoryQuotationRepository quotations = new InMemoryQuotationRepository();
    private final List<Object> published = new ArrayList<>();
    private final QuotationCommandService service = new QuotationCommandService(
            transportRequests, quotations, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

    private TransportRequest submitted() {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                NUMBER,
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                new UtcInstant(NOW.minusSeconds(3600)));
        transportRequests.save(request);
        return transportRequests.findById(request.id()).orElseThrow();
    }

    private void approved() {
        TransportRequest request = submitted();
        request.approve(1, STAFF, "根拠", new UtcInstant(NOW.minusSeconds(60)));
        transportRequests.update(request);
    }

    @Test
    void 見積り作成中の見積依頼に見積り1を算出して承認待ちで保存する() {
        approved();

        CalculationOutcome outcome =
                service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()));

        assertThat(outcome).isEqualTo(new CalculationOutcome.Calculated(NUMBER, 1));
        assertThat(quotations.findByTransportRequestIdAndNo(
                        transportRequests
                                .findByNumberForStaff(NUMBER)
                                .orElseThrow()
                                .id(),
                        1))
                .hasValueSatisfying(
                        quotation -> assertThat(quotation.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL));
        assertThat(published).isEmpty();
    }

    @Test
    void 審査中の見積依頼には見積りを作れない() {
        submitted();

        assertThat(service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput())))
                .isEqualTo(new CalculationOutcome.Rejected(QuotationRejection.TRANSPORT_REQUEST_NOT_QUOTING));
    }

    @Test
    void ない見積依頼は見つからない() {
        assertThat(service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput())))
                .isEqualTo(new CalculationOutcome.NotFound());
    }

    @Test
    void 承認待ちの見積りがあれば2つ目を作れない() {
        approved();
        service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()));

        assertThat(service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput())))
                .isEqualTo(new CalculationOutcome.Rejected(QuotationRejection.ALREADY_QUOTED));
    }

    @Test
    void 社内承認して提示すると保存と同じトランザクションでDE03を1回だけ発行する() {
        approved();
        service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()));

        PresentationOutcome outcome = service.present(new PresentQuotationCommand(NUMBER, 1, STAFF));

        assertThat(outcome).isEqualTo(new PresentationOutcome.Presented(NUMBER, 1));
        assertThat(published).singleElement().isInstanceOf(QuotationPresented.class);
    }

    @Test
    void 提示済みの見積りをもう一度提示すると拒否する() {
        approved();
        service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()));
        service.present(new PresentQuotationCommand(NUMBER, 1, STAFF));

        assertThat(service.present(new PresentQuotationCommand(NUMBER, 1, STAFF)))
                .isEqualTo(new PresentationOutcome.Rejected(QuotationRejection.NOT_PENDING_APPROVAL));
        assertThat(published).hasSize(1);
    }

    @Test
    void ない見積りの提示は見つからない() {
        approved();

        assertThat(service.present(new PresentQuotationCommand(NUMBER, 1, STAFF)))
                .isEqualTo(new PresentationOutcome.NotFound());
    }

    @Test
    void 同時に算出して見積り番号がぶつかったら見積りがすでにあるとして値で返す() {
        approved();
        InMemoryQuotationRepository racing = new InMemoryQuotationRepository() {
            @Override
            public void save(Quotation quotation) {
                throw new DuplicateQuotationException(quotation.transportRequestId(), quotation.quotationNo());
            }
        };
        QuotationCommandService racingService = new QuotationCommandService(
                transportRequests, racing, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(racingService.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput())))
                .isEqualTo(new CalculationOutcome.Rejected(QuotationRejection.ALREADY_QUOTED));
    }
}
