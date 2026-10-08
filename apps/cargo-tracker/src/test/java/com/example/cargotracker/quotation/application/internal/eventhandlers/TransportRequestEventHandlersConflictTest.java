package com.example.cargotracker.quotation.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * 輸送要求を進める listener が、読み込んだ後にほかの listener に先に更新されても（楽観ロックの競合）、読み直して自分の更新を反映する
 * （Bolt 22 の割り込み。DE-03 と DE-16、DE-21 と DE-04 は同じ輸送要求を並行して更新し得る）。
 */
class TransportRequestEventHandlersConflictTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-08T02:00:00Z"));

    private final InMemoryTransportRequestRepository store = new InMemoryTransportRequestRepository();

    private TransportRequest stored(Consumer<TransportRequest> progress) {
        UserId staff = new UserId(UUID.randomUUID());
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                staff,
                NOW);
        store.save(request);
        TransportRequest found = store.findById(request.id()).orElseThrow();
        found.approve(1, staff, "根拠", NOW);
        progress.accept(found);
        store.update(found);
        return found;
    }

    private TransportRequestStatus statusOf(TransportRequest request) {
        return store.findById(request.id()).orElseThrow().status();
    }

    private static QuotationPresented presented(UUID transportRequestId) {
        return new QuotationPresented(UUID.randomUUID(), 1, transportRequestId, 1, NOW, List.of(), NOW, NOW, NOW);
    }

    private static RouteDesignRequested routeDesignRequested(UUID transportRequestId) {
        return new RouteDesignRequested(
                UUID.randomUUID(), 1, transportRequestId, 1, List.of(), NOW, NOW, NOW, UUID.randomUUID(), NOW);
    }

    private static QuotationRouteAssigned assigned(UUID transportRequestId) {
        return new QuotationRouteAssigned(UUID.randomUUID(), 1, transportRequestId, 1, "RC-2026-0001", 1, NOW);
    }

    private static QuotationApprovedByShipper approved(UUID transportRequestId) {
        return new QuotationApprovedByShipper(
                UUID.randomUUID(), 1, transportRequestId, 1, "RC-2026-0001", 1, UUID.randomUUID(), NOW);
    }

    @Test
    void DE_03を読み込んだ後にDE_16が先に更新しても競合で失敗せず経路設計中のままにする() {
        TransportRequest request = stored(_ -> {});
        RouteDesignRequestedEventHandler routeDesign = new RouteDesignRequestedEventHandler(store);
        QuotationPresentedEventHandler presentation = new QuotationPresentedEventHandler(new InterleavingRepository(
                store, () -> routeDesign.on(routeDesignRequested(request.id().value()))));

        presentation.on(presented(request.id().value()));

        assertThat(statusOf(request)).isEqualTo(TransportRequestStatus.ROUTING);
    }

    @Test
    void DE_16を読み込んだ後にDE_03が先に更新しても競合で失敗せず経路設計中にする() {
        TransportRequest request = stored(_ -> {});
        QuotationPresentedEventHandler presentation = new QuotationPresentedEventHandler(store);
        RouteDesignRequestedEventHandler routeDesign = new RouteDesignRequestedEventHandler(new InterleavingRepository(
                store, () -> presentation.on(presented(request.id().value()))));

        routeDesign.on(routeDesignRequested(request.id().value()));

        assertThat(statusOf(request)).isEqualTo(TransportRequestStatus.ROUTING);
    }

    @Test
    void DE_04を読み込んだ後にDE_21が先に更新しても競合で失敗せず予約待ちにする() {
        TransportRequest request = stored(found -> found.markRoutingRequested(1));
        QuotationRouteAssignedEventHandler assignment = new QuotationRouteAssignedEventHandler(store);
        QuotationApprovedByShipperEventHandler approval =
                new QuotationApprovedByShipperEventHandler(new InterleavingRepository(
                        store, () -> assignment.on(assigned(request.id().value()))));

        approval.on(approved(request.id().value()));

        assertThat(statusOf(request)).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
    }

    @Test
    void DE_21を読み込んだ後にDE_04が先に更新しても競合で失敗せず予約待ちのままにする() {
        TransportRequest request = stored(found -> found.markRoutingRequested(1));
        QuotationApprovedByShipperEventHandler approval = new QuotationApprovedByShipperEventHandler(store);
        QuotationRouteAssignedEventHandler assignment =
                new QuotationRouteAssignedEventHandler(new InterleavingRepository(
                        store, () -> approval.on(approved(request.id().value()))));

        assignment.on(assigned(request.id().value()));

        assertThat(statusOf(request)).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
    }

    @Test
    void 競合が続くときは上限の回数だけ読み直した後に例外を投げ発行の記録を未完了に残す() {
        TransportRequest request = stored(_ -> {});
        AlwaysConflictingRepository conflicting = new AlwaysConflictingRepository(store);
        RouteDesignRequestedEventHandler routeDesign = new RouteDesignRequestedEventHandler(conflicting);

        assertThatThrownBy(
                        () -> routeDesign.on(routeDesignRequested(request.id().value())))
                .isInstanceOf(ConcurrentTransportRequestUpdateException.class);
        assertThat(conflicting.updates).isEqualTo(3);
        assertThat(statusOf(request)).isEqualTo(TransportRequestStatus.QUOTING);
    }

    /** 最初の読み込みの直後に、ほかの listener の処理（別のトランザクションのコミット）を割り込ませる。 */
    private static final class InterleavingRepository extends DelegatingRepository {

        private Runnable interleaved;

        InterleavingRepository(TransportRequestRepository delegate, Runnable interleaved) {
            super(delegate);
            this.interleaved = interleaved;
        }

        @Override
        public Optional<TransportRequest> findById(TransportRequestId id) {
            Optional<TransportRequest> found = super.findById(id);
            if (interleaved != null) {
                Runnable other = interleaved;
                interleaved = null;
                other.run();
            }
            return found;
        }
    }

    /** 更新のたびに、ほかの更新が先に保存されていたことにする。 */
    private static final class AlwaysConflictingRepository extends DelegatingRepository {

        private int updates;

        AlwaysConflictingRepository(TransportRequestRepository delegate) {
            super(delegate);
        }

        @Override
        public void update(TransportRequest transportRequest) {
            updates++;
            throw new ConcurrentTransportRequestUpdateException(
                    transportRequest.id(), transportRequest.aggregateVersion());
        }
    }

    private static class DelegatingRepository implements TransportRequestRepository {

        private final TransportRequestRepository delegate;

        DelegatingRepository(TransportRequestRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void save(TransportRequest transportRequest) {
            delegate.save(transportRequest);
        }

        @Override
        public void update(TransportRequest transportRequest) {
            delegate.update(transportRequest);
        }

        @Override
        public Optional<TransportRequest> findById(TransportRequestId id) {
            return delegate.findById(id);
        }

        @Override
        public Optional<TransportRequest> findByNumber(TransportRequestNumber number, CompanyId shipperCompanyId) {
            return delegate.findByNumber(number, shipperCompanyId);
        }

        @Override
        public List<TransportRequestSummary> findSummariesByShipper(CompanyId shipperCompanyId) {
            return delegate.findSummariesByShipper(shipperCompanyId);
        }

        @Override
        public Optional<TransportRequest> findByNumberForStaff(TransportRequestNumber number) {
            return delegate.findByNumberForStaff(number);
        }

        @Override
        public List<TransportRequestSummary> findUnderReviewSummaries() {
            return delegate.findUnderReviewSummaries();
        }

        @Override
        public List<TransportRequestSummary> findQuotingSummaries() {
            return delegate.findQuotingSummaries();
        }
    }
}
