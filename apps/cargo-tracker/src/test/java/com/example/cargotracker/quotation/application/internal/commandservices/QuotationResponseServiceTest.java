package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commands.ApproveQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.ConsultQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.DeclineQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationConsultationRequested;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
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

/** 荷主の見積りへの回答（US-24 AC1、Q-INV-07・08・09、DE-16。Bolt 12）。 */
class QuotationResponseServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T02:00:00Z");
    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final CompanyId SHIPPER = new CompanyId(UUID.randomUUID());
    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final UserId RESPONDENT = new UserId(UUID.randomUUID());

    private final InMemoryTransportRequestRepository transportRequests = new InMemoryTransportRequestRepository();
    private final InMemoryQuotationRepository quotations = new InMemoryQuotationRepository();
    private final List<Object> published = new ArrayList<>();
    private final QuotationResponseService service = new QuotationResponseService(
            transportRequests, quotations, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

    private TransportRequestId quotedRequest() {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                NUMBER,
                SHIPPER,
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                new UtcInstant(NOW.minusSeconds(7200)));
        transportRequests.save(request);
        TransportRequest found = transportRequests.findById(request.id()).orElseThrow();
        found.approve(1, STAFF, "根拠", new UtcInstant(NOW.minusSeconds(3600)));
        found.markQuotationPresented(1);
        transportRequests.update(found);
        return found.id();
    }

    private Quotation saveQuotation(TransportRequestId requestId, boolean present) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), requestId, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), new UtcInstant(NOW.minusSeconds(1800)));
        if (present) {
            quotation.presentInternally(STAFF, new UtcInstant(NOW.minusSeconds(900)));
            quotation.clearDomainEvents();
        }
        quotations.save(quotation);
        return quotation;
    }

    private RequestRouteDesignCommand command(CompanyId shipper) {
        return new RequestRouteDesignCommand(NUMBER, 1, shipper, RESPONDENT);
    }

    @Test
    void 提示済みの見積りに回答すると詳細設計依頼済みで保存しDE16を発行する() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, true);

        assertThat(service.requestRouteDesign(command(SHIPPER)))
                .isEqualTo(new RouteDesignRequestOutcome.Requested(NUMBER, 1));

        assertThat(quotations.findByTransportRequestIdAndNo(requestId, 1)).hasValueSatisfying(quotation -> {
            assertThat(quotation.status()).isEqualTo(QuotationStatus.ROUTING_REQUESTED);
            assertThat(quotation.respondedBy()).contains(RESPONDENT);
            assertThat(quotation.respondedAt()).contains(new UtcInstant(NOW));
        });
        assertThat(published).singleElement().isInstanceOf(RouteDesignRequested.class);
    }

    @Test
    void 他社の見積依頼と荷主に提示していない見積りとない番号は見つからない() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, false);

        assertThat(service.requestRouteDesign(command(new CompanyId(UUID.randomUUID()))))
                .isEqualTo(new RouteDesignRequestOutcome.NotFound());
        assertThat(service.requestRouteDesign(command(SHIPPER))).isEqualTo(new RouteDesignRequestOutcome.NotFound());
        assertThat(service.requestRouteDesign(new RequestRouteDesignCommand(NUMBER, 9, SHIPPER, RESPONDENT)))
                .isEqualTo(new RouteDesignRequestOutcome.NotFound());
        assertThat(published).isEmpty();
    }

    @Test
    void 有効期限と同時刻の回答は失効として拒否し何も保存しない() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, true);
        QuotationResponseService later = new QuotationResponseService(
                transportRequests,
                quotations,
                published::add,
                Clock.fixed(QuotationFixture.EXPIRES_AT.instant(), ZoneOffset.UTC));

        assertThat(later.requestRouteDesign(command(SHIPPER)))
                .isEqualTo(new RouteDesignRequestOutcome.Rejected(QuotationRejection.EXPIRED));
        assertThat(quotations
                        .findByTransportRequestIdAndNo(requestId, 1)
                        .orElseThrow()
                        .status())
                .isEqualTo(QuotationStatus.PRESENTED);
        assertThat(published).isEmpty();
    }

    @Test
    void 同時に更新されたときは回答を受け付けずイベントも発行しない() {
        TransportRequestId requestId = quotedRequest();
        InMemoryQuotationRepository racing = new InMemoryQuotationRepository() {
            @Override
            public void update(Quotation quotation) {
                throw new ConcurrentQuotationUpdateException(quotation.id(), quotation.aggregateVersion());
            }
        };
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), requestId, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), new UtcInstant(NOW.minusSeconds(1800)));
        quotation.presentInternally(STAFF, new UtcInstant(NOW.minusSeconds(900)));
        quotation.clearDomainEvents();
        racing.save(quotation);
        QuotationResponseService racingService = new QuotationResponseService(
                transportRequests, racing, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(racingService.requestRouteDesign(command(SHIPPER)))
                .isEqualTo(new RouteDesignRequestOutcome.Conflict());
        assertThat(published).isEmpty();
    }

    // 荷主の辞退（US-24 AC2。W9 の Bolt 46）

    private DeclineQuotationCommand declineCommand(CompanyId shipper, String reason) {
        return new DeclineQuotationCommand(NUMBER, 1, shipper, RESPONDENT, reason);
    }

    @Test
    void 提示済みの見積りを理由を付けて辞退すると失効として保存しイベントは発行しない() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, true);

        assertThat(service.decline(declineCommand(SHIPPER, "他社で進めるため")))
                .isEqualTo(new DeclineOutcome.Declined(NUMBER, 1));

        assertThat(quotations.findByTransportRequestIdAndNo(requestId, 1)).hasValueSatisfying(quotation -> {
            assertThat(quotation.status()).isEqualTo(QuotationStatus.EXPIRED);
            assertThat(quotation.declineReason()).contains("他社で進めるため");
            assertThat(quotation.respondedBy()).contains(RESPONDENT);
            assertThat(quotation.respondedAt()).contains(new UtcInstant(NOW));
        });
        assertThat(published).isEmpty();
    }

    @Test
    void 理由が空か長すぎる辞退は受け付けず何も保存しない() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, true);

        assertThat(service.decline(declineCommand(SHIPPER, "  ")))
                .isEqualTo(new DeclineOutcome.InvalidReason("辞退の理由を入力してください"));
        assertThat(service.decline(declineCommand(SHIPPER, "あ".repeat(501))))
                .isEqualTo(new DeclineOutcome.InvalidReason("辞退の理由は 500 文字以内で入力してください"));

        assertThat(quotations
                        .findByTransportRequestIdAndNo(requestId, 1)
                        .orElseThrow()
                        .status())
                .isEqualTo(QuotationStatus.PRESENTED);
    }

    @Test
    void 他社の見積依頼と荷主に提示していない見積りの辞退は見つからない() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, false);

        assertThat(service.decline(declineCommand(new CompanyId(UUID.randomUUID()), "理由")))
                .isEqualTo(new DeclineOutcome.NotFound());
        assertThat(service.decline(declineCommand(SHIPPER, "理由"))).isEqualTo(new DeclineOutcome.NotFound());
    }

    @Test
    void 有効期限と同時刻の辞退と回答済みの見積りの辞退は拒否する() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, true);
        QuotationResponseService later = new QuotationResponseService(
                transportRequests,
                quotations,
                published::add,
                Clock.fixed(QuotationFixture.EXPIRES_AT.instant(), ZoneOffset.UTC));

        assertThat(later.decline(declineCommand(SHIPPER, "理由")))
                .isEqualTo(new DeclineOutcome.Rejected(QuotationRejection.EXPIRED));

        service.requestRouteDesign(command(SHIPPER));
        assertThat(service.decline(declineCommand(SHIPPER, "理由")))
                .isEqualTo(new DeclineOutcome.Rejected(QuotationRejection.ROUTING_REQUESTED));
    }

    @Test
    void 同時に更新されたときは辞退を受け付けない() {
        TransportRequestId requestId = quotedRequest();
        InMemoryQuotationRepository racing = new InMemoryQuotationRepository() {
            @Override
            public void update(Quotation quotation) {
                throw new ConcurrentQuotationUpdateException(quotation.id(), quotation.aggregateVersion());
            }
        };
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), requestId, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), new UtcInstant(NOW.minusSeconds(1800)));
        quotation.presentInternally(STAFF, new UtcInstant(NOW.minusSeconds(900)));
        quotation.clearDomainEvents();
        racing.save(quotation);
        QuotationResponseService racingService = new QuotationResponseService(
                transportRequests, racing, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(racingService.decline(declineCommand(SHIPPER, "理由"))).isEqualTo(new DeclineOutcome.Conflict());
    }

    // 荷主の相談（US-24 AC3。W9 の Bolt 46-1）

    private ConsultQuotationCommand consultCommand(CompanyId shipper, String content) {
        return new ConsultQuotationCommand(NUMBER, 1, shipper, RESPONDENT, content);
    }

    @Test
    void 提示済みの見積りの条件を相談すると相談のイベントを発行し見積りの状態は変えない() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, true);

        assertThat(service.consult(consultCommand(SHIPPER, "納期を早められるか")))
                .isEqualTo(new ConsultOutcome.Requested(NUMBER, 1));

        assertThat(quotations
                        .findByTransportRequestIdAndNo(requestId, 1)
                        .orElseThrow()
                        .status())
                .isEqualTo(QuotationStatus.PRESENTED);
        assertThat(published).singleElement().isInstanceOfSatisfying(QuotationConsultationRequested.class, event -> {
            assertThat(event.transportRequestNumber()).isEqualTo("TR-2026-0001");
            assertThat(event.shipperCompanyId()).isEqualTo(SHIPPER.value());
            assertThat(event.requestedBy()).isEqualTo(RESPONDENT.value());
            assertThat(event.content()).isEqualTo("納期を早められるか");
            assertThat(event.requestedAt()).isEqualTo(new UtcInstant(NOW));
        });
    }

    @Test
    void 内容が空か長すぎる相談は受け付けずイベントも発行しない() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, true);

        assertThat(service.consult(consultCommand(SHIPPER, " ")))
                .isEqualTo(new ConsultOutcome.InvalidContent("相談内容を入力してください"));
        assertThat(service.consult(consultCommand(SHIPPER, "あ".repeat(501))))
                .isEqualTo(new ConsultOutcome.InvalidContent("相談内容は 500 文字以内で入力してください"));
        assertThat(published).isEmpty();
    }

    @Test
    void 他社の見積依頼と荷主に提示していない見積りの相談は見つからず期限後と回答済みは拒否する() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, true);

        assertThat(service.consult(consultCommand(new CompanyId(UUID.randomUUID()), "相談")))
                .isEqualTo(new ConsultOutcome.NotFound());
        QuotationResponseService later = new QuotationResponseService(
                transportRequests,
                quotations,
                published::add,
                Clock.fixed(QuotationFixture.EXPIRES_AT.instant(), ZoneOffset.UTC));
        assertThat(later.consult(consultCommand(SHIPPER, "相談")))
                .isEqualTo(new ConsultOutcome.Rejected(QuotationRejection.EXPIRED));
        service.requestRouteDesign(command(SHIPPER));
        published.clear();
        assertThat(service.consult(consultCommand(SHIPPER, "相談")))
                .isEqualTo(new ConsultOutcome.Rejected(QuotationRejection.ROUTING_REQUESTED));
        assertThat(published).isEmpty();
    }

    // 荷主の承認（US-24 AC4・AC5、Q-INV-07・08・10、DE-04。Bolt 20）

    private Quotation awaitingApproval(TransportRequestId requestId, InMemoryQuotationRepository repository) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), requestId, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), new UtcInstant(NOW.minusSeconds(1800)));
        quotation.presentInternally(STAFF, new UtcInstant(NOW.minusSeconds(900)));
        quotation.requestRouteDesign(RESPONDENT, new UtcInstant(NOW.minusSeconds(600)));
        quotation.assignRoute(QuotationFixture.assignedRoute(), new UtcInstant(NOW.minusSeconds(300)));
        quotation.clearDomainEvents();
        repository.save(quotation);
        return quotation;
    }

    private ApproveQuotationCommand approval(CompanyId shipper) {
        return new ApproveQuotationCommand(NUMBER, 1, shipper, RESPONDENT);
    }

    @Test
    void 荷主承認待ちの見積りを承認すると承認済みで保存しDE04を発行する() {
        TransportRequestId requestId = quotedRequest();
        awaitingApproval(requestId, quotations);

        assertThat(service.approve(approval(SHIPPER))).isEqualTo(new ShipperApprovalOutcome.Approved(NUMBER, 1));

        assertThat(quotations
                        .findByTransportRequestIdAndNo(requestId, 1)
                        .orElseThrow()
                        .status())
                .isEqualTo(QuotationStatus.APPROVED);
        assertThat(published).singleElement().isInstanceOf(QuotationApprovedByShipper.class);
    }

    @Test
    void 他社の見積依頼とない番号は見つからず割当ての前は拒否する() {
        TransportRequestId requestId = quotedRequest();
        saveQuotation(requestId, true);

        assertThat(service.approve(approval(new CompanyId(UUID.randomUUID()))))
                .isEqualTo(new ShipperApprovalOutcome.NotFound());
        assertThat(service.approve(new ApproveQuotationCommand(NUMBER, 9, SHIPPER, RESPONDENT)))
                .isEqualTo(new ShipperApprovalOutcome.NotFound());
        assertThat(service.approve(approval(SHIPPER)))
                .isEqualTo(new ShipperApprovalOutcome.Rejected(QuotationRejection.NOT_AWAITING_SHIPPER_APPROVAL));
        assertThat(published).isEmpty();
    }

    @Test
    void 同時に更新されたときは承認を受け付けずイベントも発行しない() {
        TransportRequestId requestId = quotedRequest();
        InMemoryQuotationRepository racing = new InMemoryQuotationRepository() {
            @Override
            public void update(Quotation quotation) {
                throw new ConcurrentQuotationUpdateException(quotation.id(), quotation.aggregateVersion());
            }
        };
        awaitingApproval(requestId, racing);
        QuotationResponseService racingService = new QuotationResponseService(
                transportRequests, racing, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(racingService.approve(approval(SHIPPER))).isEqualTo(new ShipperApprovalOutcome.Conflict());
        assertThat(published).isEmpty();
    }
}
