package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequoteQuotationCommand;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestScope;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.DuplicateQuotationException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class QuotationCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T04:00:00Z");
    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER = new UserId(UUID.randomUUID());
    private static final Instant RESPONDED_AT = NOW.minusSeconds(30);
    private static final CompanyId SHIPPER_COMPANY = new CompanyId(UUID.randomUUID());
    /** 営業担当者（見積依頼の荷主企業を担当する。Bolt 38f）。 */
    private static final AuthenticatedActor OPERATOR = AuthenticatedActorFixture.salesHandling(STAFF, SHIPPER_COMPANY);

    private final InMemoryTransportRequestRepository transportRequests = new InMemoryTransportRequestRepository();
    private final InMemoryQuotationRepository quotations = new InMemoryQuotationRepository();
    private final List<Object> published = new ArrayList<>();
    private final QuotationCommandService service = new QuotationCommandService(
            quotations,
            published::add,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new StaffTransportRequestScope(transportRequests));

    private TransportRequest submitted() {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                NUMBER,
                SHIPPER_COMPANY,
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
                service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()), OPERATOR);

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

        assertThat(service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new CalculationOutcome.Rejected(QuotationRejection.TRANSPORT_REQUEST_NOT_QUOTING));
    }

    @Test
    void ない見積依頼は見つからない() {
        assertThat(service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new CalculationOutcome.NotFound());
    }

    @Test
    void 承認待ちの見積りがあれば2つ目を作れない() {
        approved();
        service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()), OPERATOR);

        assertThat(service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new CalculationOutcome.Rejected(QuotationRejection.ALREADY_QUOTED));
    }

    @Test
    void 社内承認して提示すると保存と同じトランザクションでDE03を1回だけ発行する() {
        approved();
        service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()), OPERATOR);

        PresentationOutcome outcome = service.present(new PresentQuotationCommand(NUMBER, 1, STAFF), OPERATOR);

        assertThat(outcome).isEqualTo(new PresentationOutcome.Presented(NUMBER, 1));
        assertThat(published).singleElement().isInstanceOf(QuotationPresented.class);
    }

    @Test
    void 提示済みの見積りをもう一度提示すると拒否する() {
        approved();
        service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()), OPERATOR);
        service.present(new PresentQuotationCommand(NUMBER, 1, STAFF), OPERATOR);

        assertThat(service.present(new PresentQuotationCommand(NUMBER, 1, STAFF), OPERATOR))
                .isEqualTo(new PresentationOutcome.Rejected(QuotationRejection.NOT_PENDING_APPROVAL));
        assertThat(published).hasSize(1);
    }

    @Test
    void ない見積りの提示は見つからない() {
        approved();

        assertThat(service.present(new PresentQuotationCommand(NUMBER, 1, STAFF), OPERATOR))
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
                racing,
                published::add,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new StaffTransportRequestScope(transportRequests));

        assertThat(racingService.calculate(
                        new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new CalculationOutcome.Rejected(QuotationRejection.ALREADY_QUOTED));
    }

    private TransportRequestId presentedQuotation1() {
        approved();
        service.calculate(new CalculateQuotationCommand(NUMBER, QuotationFixture.completeInput()), OPERATOR);
        service.present(new PresentQuotationCommand(NUMBER, 1, STAFF), OPERATOR);
        published.clear();
        return transportRequests.findByNumberForStaff(NUMBER).orElseThrow().id();
    }

    @Test
    void 提示済みの見積りを再見積りすると旧版を置換済みにして次の番号の見積りを承認待ちで作る() {
        TransportRequestId id = presentedQuotation1();

        RequotationOutcome outcome =
                service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()), OPERATOR);

        assertThat(outcome).isEqualTo(new RequotationOutcome.Calculated(NUMBER, 2, QuotationStatus.REPLACED));
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

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new RequotationOutcome.Calculated(NUMBER, 2, QuotationStatus.REPLACED));
    }

    @Test
    void 再見積りの入力に誤りがあれば何も変えずに違反を返す() {
        TransportRequestId id = presentedQuotation1();
        QuotationInput empty = new QuotationInput(List.of(), null, null, List.of(), null, null);

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, empty), OPERATOR))
                .isInstanceOf(RequotationOutcome.Invalid.class);
        assertThat(quotations.findByTransportRequestIdAndNo(id, 1))
                .hasValueSatisfying(old -> assertThat(old.status()).isEqualTo(QuotationStatus.PRESENTED));
        assertThat(quotations.findByTransportRequestIdAndNo(id, 2)).isEmpty();
    }

    @Test
    void 置換済みの見積りは再見積りできない() {
        presentedQuotation1();
        service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()), OPERATOR);

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new RequotationOutcome.Rejected(QuotationRejection.REPLACED));
    }

    @Test
    void ない見積りの再見積りは見つからない() {
        approved();

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()), OPERATOR))
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
                racing,
                published::add,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new StaffTransportRequestScope(transportRequests));

        assertThat(racingService.requote(
                        new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new RequotationOutcome.Conflict());
        assertThat(racing.findByTransportRequestIdAndNo(id, 2)).isEmpty();
    }

    @Test
    void 有効期限を過ぎた見積りを再見積りすると旧版が失効になったことを返す() {
        presentedQuotation1();
        QuotationCommandService later = new QuotationCommandService(
                quotations,
                published::add,
                Clock.fixed(QuotationFixture.EXPIRES_AT.instant(), ZoneOffset.UTC),
                new StaffTransportRequestScope(transportRequests));
        QuotationInput input = new QuotationInput(
                QuotationFixture.completeInput().lines(),
                QuotationFixture.completeInput().currency(),
                new UtcInstant(QuotationFixture.EXPIRES_AT.instant().plusSeconds(86_400)),
                List.of(),
                new UtcInstant(QuotationFixture.EXPIRES_AT.instant().plusSeconds(172_800)),
                new UtcInstant(QuotationFixture.EXPIRES_AT.instant().plusSeconds(259_200)));

        assertThat(later.requote(new RequoteQuotationCommand(NUMBER, 1, input), OPERATOR))
                .isEqualTo(new RequotationOutcome.Calculated(NUMBER, 2, QuotationStatus.EXPIRED));
    }

    // 荷主の承認の後に失効した見積りの再見積り（D-85。Bolt 35b）

    /** 見積り 1 を荷主が承認し、見積依頼を指定の状態まで進める（配信の遅れのときは途中の状態で止める）。 */
    private void approvedQuotation1(TransportRequestStatus target) {
        TransportRequestId id = presentedQuotation1();
        Quotation quotation = quotations.findByTransportRequestIdAndNo(id, 1).orElseThrow();
        UtcInstant at = new UtcInstant(NOW);
        quotation.requestRouteDesign(SHIPPER, new UtcInstant(RESPONDED_AT));
        quotation.assignRoute(QuotationFixture.assignedRoute(), at);
        quotation.approveByShipper(SHIPPER, at);
        quotations.update(quotation);
        TransportRequest request = transportRequests.findById(id).orElseThrow();
        request.markQuotationPresented(1);
        request.markRoutingRequested(1);
        if (target != TransportRequestStatus.ROUTING) {
            request.markAwaitingApproval(1);
        }
        if (target == TransportRequestStatus.READY_TO_BOOK || target == TransportRequestStatus.BOOKED) {
            request.markReadyToBook(1);
        }
        if (target == TransportRequestStatus.BOOKED) {
            request.markBooked(1);
        }
        transportRequests.update(request);
        assertThat(transportRequests.findById(id).orElseThrow().status())
                .as("前提")
                .isEqualTo(target);
    }

    private QuotationCommandService atExpiry() {
        return new QuotationCommandService(
                quotations,
                published::add,
                Clock.fixed(QuotationFixture.EXPIRES_AT.instant(), ZoneOffset.UTC),
                new StaffTransportRequestScope(transportRequests));
    }

    private static QuotationInput laterInput() {
        return new QuotationInput(
                QuotationFixture.completeInput().lines(),
                QuotationFixture.completeInput().currency(),
                new UtcInstant(QuotationFixture.EXPIRES_AT.instant().plusSeconds(86_400)),
                List.of(),
                new UtcInstant(QuotationFixture.EXPIRES_AT.instant().plusSeconds(172_800)),
                new UtcInstant(QuotationFixture.EXPIRES_AT.instant().plusSeconds(259_200)));
    }

    @Test
    void 予約待ちの見積依頼で承認済みで失効した見積りを再見積りすると旧版を失効にし新しい見積りは経路と荷主の回答を引き継ぐ() {
        approvedQuotation1(TransportRequestStatus.READY_TO_BOOK);
        TransportRequestId id =
                transportRequests.findByNumberForStaff(NUMBER).orElseThrow().id();

        assertThat(atExpiry().requote(new RequoteQuotationCommand(NUMBER, 1, laterInput()), OPERATOR))
                .isEqualTo(new RequotationOutcome.Calculated(NUMBER, 2, QuotationStatus.EXPIRED));

        Quotation replacement = quotations.findByTransportRequestIdAndNo(id, 2).orElseThrow();
        assertThat(replacement.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
        assertThat(replacement.assignedRoute()).contains(QuotationFixture.assignedRoute());
        assertThat(replacement.respondedBy()).contains(SHIPPER);
        assertThat(replacement.respondedAt()).contains(new UtcInstant(RESPONDED_AT));
        assertThat(replacement.routePolicy())
                .as("入力の経路方針でなく旧版の経路方針")
                .isEqualTo(quotations
                        .findByTransportRequestIdAndNo(id, 1)
                        .orElseThrow()
                        .routePolicy());
        assertThat(quotations.findByTransportRequestIdAndNo(id, 1)).hasValueSatisfying(old -> {
            assertThat(old.status()).isEqualTo(QuotationStatus.EXPIRED);
            assertThat(old.shipperApproval()).isPresent();
        });
        assertThat(published).isEmpty();
    }

    @ParameterizedTest(name = "見積依頼が {0}（配信の遅れ）")
    @EnumSource(names = {"ROUTING", "AWAITING_APPROVAL"})
    void 経路設計中と荷主承認待ちの見積依頼でも承認済みで失効した見積りを再見積りできる(TransportRequestStatus status) {
        approvedQuotation1(status);

        assertThat(atExpiry().requote(new RequoteQuotationCommand(NUMBER, 1, laterInput()), OPERATOR))
                .isEqualTo(new RequotationOutcome.Calculated(NUMBER, 2, QuotationStatus.EXPIRED));
    }

    @Test
    void 期限内の承認済みの見積りは再見積りできず何も保存しない() {
        approvedQuotation1(TransportRequestStatus.READY_TO_BOOK);
        TransportRequestId id =
                transportRequests.findByNumberForStaff(NUMBER).orElseThrow().id();

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new RequotationOutcome.Rejected(QuotationRejection.APPROVED_AND_STILL_VALID));
        assertThat(quotations.findByTransportRequestIdAndNo(id, 1))
                .hasValueSatisfying(old -> assertThat(old.status()).isEqualTo(QuotationStatus.APPROVED));
        assertThat(quotations.findByTransportRequestIdAndNo(id, 2)).isEmpty();
    }

    @Test
    void 輸送要求の古い版に対する見積りは再見積りできない() {
        TransportRequest request = submitted();
        UtcInstant at = new UtcInstant(NOW.minusSeconds(60));
        request.sendBack(1, STAFF, "目的地を直してください", "", at);
        request.resubmit(ShipmentTermsFixture.generalCargo(), STAFF, at);
        request.approve(2, STAFF, "根拠", at);
        transportRequests.update(request);
        Quotation old = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        old.calculate(QuotationFixture.completeInput(), at);
        quotations.save(old);

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new RequotationOutcome.Rejected(QuotationRejection.OUTDATED_VERSION));
    }

    @Test
    void 予約確定済みの見積依頼では承認済みで失効した見積りも再見積りできず何も保存しない() {
        approvedQuotation1(TransportRequestStatus.BOOKED);
        TransportRequestId id =
                transportRequests.findByNumberForStaff(NUMBER).orElseThrow().id();

        assertThat(atExpiry().requote(new RequoteQuotationCommand(NUMBER, 1, laterInput()), OPERATOR))
                .as("画面と同じ照会の判定（D-85。Bolt 35c）")
                .isEqualTo(new RequotationOutcome.Rejected(QuotationRejection.TRANSPORT_REQUEST_BOOKED));
        assertThat(quotations.findByTransportRequestIdAndNo(id, 1))
                .hasValueSatisfying(old -> assertThat(old.status()).isEqualTo(QuotationStatus.APPROVED));
        assertThat(quotations.findByTransportRequestIdAndNo(id, 2)).isEmpty();
    }

    @ParameterizedTest(name = "見積依頼が {0}")
    @EnumSource(names = {"DRAFT", "UNDER_REVIEW"})
    void 差し戻しと審査中の見積依頼の見積りは再見積りできない(TransportRequestStatus status) {
        TransportRequest request = submitted();
        if (status == TransportRequestStatus.DRAFT) {
            request.sendBack(1, STAFF, "目的地を直してください", "", new UtcInstant(NOW.minusSeconds(60)));
            transportRequests.update(request);
        }
        assertThat(transportRequests.findById(request.id()).orElseThrow().status())
                .as("前提")
                .isEqualTo(status);
        Quotation old = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        old.calculate(QuotationFixture.completeInput(), new UtcInstant(NOW.minusSeconds(60)));
        quotations.save(old);

        assertThat(service.requote(new RequoteQuotationCommand(NUMBER, 1, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new RequotationOutcome.Rejected(QuotationRejection.TRANSPORT_REQUEST_NOT_QUOTING));
    }
}
