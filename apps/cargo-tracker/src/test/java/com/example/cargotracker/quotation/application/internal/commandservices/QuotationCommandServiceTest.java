package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequoteQuotationCommand;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.DuplicateQuotationException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
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

    private TransportRequestId presentedQuotation1() {
        approved();
        service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()));
        service.present(new PresentQuotationCommand(NUMBER, 1, STAFF));
        published.clear();
        return transportRequests.findByNumberForStaff(NUMBER).orElseThrow().id();
    }

    @Test
    void 提示済みの見積りを再見積りすると旧版を置換済みにして次の番号の見積りを承認待ちで作る() {
        TransportRequestId id = presentedQuotation1();

        RequotationOutcome outcome =
                service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()));

        assertThat(outcome).isEqualTo(new RequotationOutcome.Calculated(NUMBER, 2));
        Quotation replacement = quotations.findByTransportRequestIdAndNo(id, 2).orElseThrow();
        assertThat(replacement.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
        assertThat(quotations.findByTransportRequestIdAndNo(id, 1)).hasValueSatisfying(old -> {
            assertThat(old.status()).isEqualTo(QuotationStatus.REPLACED);
            assertThat(old.replacedBy()).contains(replacement.id());
        });
        assertThat(published).isEmpty();
    }

    @Test
    void 見積提示済みの見積依頼でも再見積りできる() {
        presentedQuotation1();
        TransportRequest request =
                transportRequests.findByNumberForStaff(NUMBER).orElseThrow();
        request.markQuotationPresented(1);
        transportRequests.update(request);

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput())))
                .isEqualTo(new RequotationOutcome.Calculated(NUMBER, 2));
    }

    @Test
    void 再見積りの入力に誤りがあれば何も変えずに違反を返す() {
        TransportRequestId id = presentedQuotation1();
        QuotationInput empty = new QuotationInput(List.of(), null, null, List.of(), null, null);

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, empty)))
                .isInstanceOf(RequotationOutcome.Invalid.class);
        assertThat(quotations.findByTransportRequestIdAndNo(id, 1))
                .hasValueSatisfying(old -> assertThat(old.status()).isEqualTo(QuotationStatus.PRESENTED));
        assertThat(quotations.findByTransportRequestIdAndNo(id, 2)).isEmpty();
    }

    @Test
    void 置換済みの見積りは再見積りできない() {
        presentedQuotation1();
        service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()));

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput())))
                .isEqualTo(new RequotationOutcome.Rejected(QuotationRejection.REPLACED));
    }

    @Test
    void ない見積りの再見積りは見つからない() {
        approved();

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput())))
                .isEqualTo(new RequotationOutcome.NotFound());
    }

    @Test
    void 旧版をほかの利用者が先に更新していたら競合として返し新しい見積りを作らない() {
        TransportRequestId id = presentedQuotation1();
        InMemoryQuotationRepository racing = new InMemoryQuotationRepository() {
            @Override
            public void update(Quotation quotation) {
                throw new ConcurrentQuotationUpdateException(quotation.id(), quotation.aggregateVersion());
            }
        };
        quotations.findByTransportRequestId(id).forEach(racing::save);
        QuotationCommandService racingService = new QuotationCommandService(
                transportRequests, racing, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(racingService.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput())))
                .isEqualTo(new RequotationOutcome.Conflict());
        assertThat(racing.findByTransportRequestIdAndNo(id, 2)).isEmpty();
    }
}
