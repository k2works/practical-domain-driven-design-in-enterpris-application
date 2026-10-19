package com.example.cargotracker.quotation.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationPresentedForReapproval;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 再見積りの提示で輸送要求を荷主承認待ちへ戻す DE-24 と、届く順の入れ替わりの守り（D-85、ADR-018。Bolt 35b2）。
 * 輸送要求を読んでから見積りを読み、DE-04・DE-24 は進めなくても書いて楽観ロックでぶつける。置換済み・失効の見積りの DE-21・DE-04 は
 * 進めない。差し込みの順は Bolt 35b2 計画の表の 1〜3。
 */
class ReapprovalOrderingEventHandlersTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-07T05:00:00Z"));
    private static final UtcInstant EXPIRES = QuotationFixture.EXPIRES_AT;

    private final InMemoryTransportRequestRepository requests = new InMemoryTransportRequestRepository();
    private final InMemoryQuotationRepository quotations = new InMemoryQuotationRepository();
    private final UserId staff = new UserId(UUID.randomUUID());
    private final UserId shipper = new UserId(UUID.randomUUID());
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<Logger> loggers = List.of(
            (Logger) LoggerFactory.getLogger(QuotationPresentedForReapprovalEventHandler.class),
            (Logger) LoggerFactory.getLogger(QuotationApprovedByShipperEventHandler.class),
            (Logger) LoggerFactory.getLogger(QuotationRouteAssignedEventHandler.class));

    private TransportRequest request;
    private Quotation old;

    @BeforeEach
    void setUp() {
        logs.start();
        loggers.forEach(logger -> logger.addAppender(logs));
        request = transportRequest();
        old = approvedQuotation1();
    }

    @AfterEach
    void releaseLogs() {
        loggers.forEach(logger -> logger.detachAppender(logs));
    }

    // DE-24 の listener

    @Test
    void 新しい見積りが荷主承認待ちなら予約待ちの輸送要求を荷主承認待ちへ戻す() {
        advance(TransportRequestStatus.READY_TO_BOOK);
        Quotation next = requotedAndPresented();

        reapproval(requests, quotations).on(reapprovalEvent(next));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 輸送要求がすでに荷主承認待ちでも新しい見積りが荷主承認待ちなら書いて版を上げる() {
        advance(TransportRequestStatus.AWAITING_APPROVAL);
        Quotation next = requotedAndPresented();
        long version = versionOf();

        reapproval(requests, quotations).on(reapprovalEvent(next));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);
        assertThat(versionOf()).isEqualTo(version + 1);
    }

    @Test
    void 新しい見積りがもう承認済みなら輸送要求を変えず書かずログも残さない() {
        advance(TransportRequestStatus.READY_TO_BOOK);
        Quotation next = requotedAndPresented();
        approve(next);
        long version = versionOf();

        reapproval(requests, quotations).on(reapprovalEvent(next));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(versionOf()).isEqualTo(version);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 再見積りで失効した見積りのDE24が遅れて届いても変えず警告のログに残す() {
        advance(TransportRequestStatus.READY_TO_BOOK);
        Quotation next = requotedAndPresented();
        approve(next);
        Quotation approvedNext = quotations.findById(next.id()).orElseThrow();
        Quotation third = calculated(3);
        approvedNext.requoteInto(third, new UtcInstant(EXPIRES.instant().plusSeconds(86_400)));
        quotations.update(approvedNext);
        quotations.save(third);
        long version = versionOf();

        reapproval(requests, quotations).on(reapprovalEvent(next));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(versionOf()).isEqualTo(version);
        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage())
                    .contains("置換済み・失効")
                    .contains(next.id().value().toString());
        });
    }

    @Test
    void 見積りが見つからないDE24は変えず警告のログに残す() {
        advance(TransportRequestStatus.READY_TO_BOOK);
        UUID missing = UUID.randomUUID();

        reapproval(requests, quotations)
                .on(new QuotationPresentedForReapproval(missing, 9, request.id().value(), 1, NOW));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage()).contains("見つからない").contains(missing.toString());
        });
    }

    @Test
    void 予約確定済みの輸送要求にはDE24が届いても変えず警告のログに残す() {
        advance(TransportRequestStatus.BOOKED);
        Quotation next = requotedAndPresented();

        reapproval(requests, quotations).on(reapprovalEvent(next));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.BOOKED);
        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage()).contains("BOOKED");
        });
    }

    // DE-04 の書き込みと、置換済み・失効の見積りのイベント

    @Test
    void 荷主の承認の再配信は予約待ちの輸送要求を変えずに書いて版を上げる() {
        advance(TransportRequestStatus.READY_TO_BOOK);
        long version = versionOf();

        approval(requests, quotations).on(approvedEvent(old));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(versionOf()).isEqualTo(version + 1);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 失効した旧版の承認と割当ては輸送要求を進めず書かず警告のログに残す() {
        advance(TransportRequestStatus.ROUTING);
        requotedAndPresented();
        long version = versionOf();

        assignment(requests, quotations).on(assignedEvent(old));
        approval(requests, quotations).on(approvedEvent(old));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.ROUTING);
        assertThat(versionOf()).isEqualTo(version);
        assertThat(logs.list)
                .hasSize(2)
                .allSatisfy(log -> assertThat(log.getFormattedMessage())
                        .contains("置換済み・失効")
                        .contains(old.id().value().toString()));
    }

    @Test
    void 見積りが見つからない承認と割当ては今までどおり輸送要求を進める() {
        UUID missing = UUID.randomUUID();
        advance(TransportRequestStatus.ROUTING);

        assignment(requests, quotations)
                .on(new QuotationRouteAssigned(missing, 1, request.id().value(), 1, "RC-2026-0001", 1, NOW));
        approval(requests, quotations)
                .on(new QuotationApprovedByShipper(
                        missing, 1, request.id().value(), 1, "RC-2026-0001", 1, shipper.value(), NOW));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 戻した後に旧版の承認が届いても荷主承認待ちのまま新しい見積りの承認で予約待ちへ進む() {
        advance(TransportRequestStatus.READY_TO_BOOK);
        Quotation next = requotedAndPresented();
        reapproval(requests, quotations).on(reapprovalEvent(next));

        approval(requests, quotations).on(approvedEvent(old));
        assertThat(statusOf()).isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);
        approve(next);
        approval(requests, quotations).on(approvedEvent(next));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
    }

    // 差し込みの順（Bolt 35b2 計画の表）

    @Test
    void 順1_DE24が見積りを読んだ後に再承認とDE04が書いてもDE24は読み直して戻さず予約待ちのまま() {
        advance(TransportRequestStatus.READY_TO_BOOK);
        Quotation next = requotedAndPresented();
        long version = versionOf();
        QuotationPresentedForReapprovalEventHandler handler =
                reapproval(requests, new InterleavingQuotations(quotations, () -> {
                    approve(next);
                    approval(requests, quotations).on(approvedEvent(next));
                }));

        handler.on(reapprovalEvent(next));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(versionOf()).as("DE-04 の書き込みの 1 回だけ（DE-24 はぶつかって戻さなかった）").isEqualTo(version + 1);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 順2_DE04が輸送要求を読んだ後にDE24が戻してもDE04は読み直して予約待ちへ進める() {
        advance(TransportRequestStatus.READY_TO_BOOK);
        Quotation next = requotedAndPresented();
        Quotation beforeApproval = quotations.findById(next.id()).orElseThrow();
        approve(next);
        long version = versionOf();
        QuotationApprovedByShipperEventHandler handler = approval(
                new InterleavingRequests(requests, () -> {
                    reapproval(requests, new StaleQuotations(quotations, beforeApproval))
                            .on(reapprovalEvent(next));
                    assertThat(statusOf())
                            .as("前提: 差し込んだ DE-24 が戻した")
                            .isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);
                }),
                quotations);

        handler.on(approvedEvent(next));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(versionOf()).as("DE-24 の戻しと、ぶつかって読み直した DE-04 の書き込み").isEqualTo(version + 2);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 順3_旧版のDE04が旧版を読んだ後に再見積りとDE24が入っても旧版のDE04は読み直して進めず荷主承認待ちのまま() {
        advance(TransportRequestStatus.AWAITING_APPROVAL);
        long version = versionOf();
        Quotation[] next = new Quotation[1];
        QuotationApprovedByShipperEventHandler handler =
                approval(requests, new InterleavingQuotations(quotations, () -> {
                    next[0] = requotedAndPresented();
                    reapproval(requests, quotations).on(reapprovalEvent(next[0]));
                }));

        handler.on(approvedEvent(old));

        assertThat(statusOf()).isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);
        assertThat(versionOf()).as("DE-24 の届いていても書く 1 回だけ").isEqualTo(version + 1);
        assertThat(logs.list)
                .singleElement()
                .satisfies(log -> assertThat(log.getFormattedMessage())
                        .contains("置換済み・失効")
                        .contains(old.id().value().toString()));
        assertThat(quotations.findById(next[0].id()).orElseThrow().status())
                .as("前提")
                .isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
    }

    // 組み立て

    private QuotationPresentedForReapprovalEventHandler reapproval(
            TransportRequestRepository requestRepository, InMemoryQuotationRepository quotationRepository) {
        return new QuotationPresentedForReapprovalEventHandler(requestRepository, quotationRepository);
    }

    private QuotationApprovedByShipperEventHandler approval(
            TransportRequestRepository requestRepository, InMemoryQuotationRepository quotationRepository) {
        return new QuotationApprovedByShipperEventHandler(requestRepository, quotationRepository);
    }

    private QuotationRouteAssignedEventHandler assignment(
            TransportRequestRepository requestRepository, InMemoryQuotationRepository quotationRepository) {
        return new QuotationRouteAssignedEventHandler(requestRepository, quotationRepository);
    }

    // 用意

    private TransportRequest transportRequest() {
        TransportRequest submitted = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                staff,
                NOW);
        requests.save(submitted);
        TransportRequest found = requests.findById(submitted.id()).orElseThrow();
        found.approve(1, staff, "根拠", NOW);
        requests.update(found);
        return found;
    }

    private void advance(TransportRequestStatus target) {
        change(found -> {
            found.markQuotationPresented(1);
            if (target == TransportRequestStatus.QUOTED) {
                return;
            }
            found.markRoutingRequested(1);
            if (target == TransportRequestStatus.ROUTING) {
                return;
            }
            found.markAwaitingApproval(1);
            if (target == TransportRequestStatus.AWAITING_APPROVAL) {
                return;
            }
            found.markReadyToBook(1);
            if (target == TransportRequestStatus.BOOKED) {
                found.markBooked(1);
            }
        });
        assertThat(statusOf()).as("前提").isEqualTo(target);
    }

    private void change(Consumer<TransportRequest> change) {
        TransportRequest found = requests.findById(request.id()).orElseThrow();
        change.accept(found);
        requests.update(found);
    }

    /** 荷主が承認した見積り 1（有効期限 2099-10-08T09:00:00Z）。 */
    private Quotation approvedQuotation1() {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), NOW);
        quotation.presentInternally(staff, NOW);
        quotation.requestRouteDesign(shipper, NOW);
        quotation.assignRoute(QuotationFixture.assignedRoute(), NOW);
        quotation.approveByShipper(shipper, NOW);
        quotations.save(quotation);
        return quotations.findById(quotation.id()).orElseThrow();
    }

    /** 見積り 1 を有効期限の時刻に再見積りし、見積り 2 を社内承認して提示する（荷主承認待ち）。 */
    private Quotation requotedAndPresented() {
        Quotation stored = quotations.findById(old.id()).orElseThrow();
        Quotation next = calculated(2);
        stored.requoteInto(next, EXPIRES);
        quotations.update(stored);
        quotations.save(next);
        Quotation saved = quotations.findById(next.id()).orElseThrow();
        saved.presentInternally(staff, EXPIRES);
        quotations.update(saved);
        return quotations.findById(next.id()).orElseThrow();
    }

    /** 有効期限を旧版より後にして算出した、版 1 の承認待ちの見積り。 */
    private Quotation calculated(int quotationNo) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, quotationNo);
        quotation.calculate(
                new QuotationInput(
                        QuotationFixture.completeInput().lines(),
                        QuotationFixture.completeInput().currency(),
                        new UtcInstant(EXPIRES.instant().plusSeconds(86_400)),
                        List.of(),
                        new UtcInstant(EXPIRES.instant().plusSeconds(172_800)),
                        new UtcInstant(EXPIRES.instant().plusSeconds(259_200))),
                EXPIRES);
        return quotation;
    }

    private void approve(Quotation quotation) {
        Quotation stored = quotations.findById(quotation.id()).orElseThrow();
        stored.approveByShipper(shipper, EXPIRES);
        quotations.update(stored);
    }

    private TransportRequestStatus statusOf() {
        return requests.findById(request.id()).orElseThrow().status();
    }

    private long versionOf() {
        return requests.findById(request.id()).orElseThrow().aggregateVersion();
    }

    private QuotationPresentedForReapproval reapprovalEvent(Quotation quotation) {
        return new QuotationPresentedForReapproval(
                quotation.id().value(), quotation.quotationNo(), request.id().value(), 1, EXPIRES);
    }

    private QuotationApprovedByShipper approvedEvent(Quotation quotation) {
        return new QuotationApprovedByShipper(
                quotation.id().value(),
                quotation.quotationNo(),
                request.id().value(),
                1,
                "RC-2026-0001",
                1,
                shipper.value(),
                NOW);
    }

    private QuotationRouteAssigned assignedEvent(Quotation quotation) {
        return new QuotationRouteAssigned(
                quotation.id().value(), quotation.quotationNo(), request.id().value(), 1, "RC-2026-0001", 1, NOW);
    }

    /** 見積りの最初の読み込みの直後に、ほかの処理（別のトランザクションのコミット）を割り込ませる。読んだ写しは割り込みの前のもの。 */
    private static final class InterleavingQuotations extends InMemoryQuotationRepository {

        private final InMemoryQuotationRepository delegate;
        private Runnable interleaved;

        InterleavingQuotations(InMemoryQuotationRepository delegate, Runnable interleaved) {
            this.delegate = delegate;
            this.interleaved = interleaved;
        }

        @Override
        public Optional<Quotation> findById(QuotationId id) {
            Optional<Quotation> found = delegate.findById(id);
            if (interleaved != null) {
                Runnable other = interleaved;
                interleaved = null;
                other.run();
            }
            return found;
        }
    }

    /** 最初の 1 回だけ、承認の前に読んだ見積りの写しを返す（DE-24 の listener が承認の前に見積りを読んだ。読み直しは今のコミットを読む）。 */
    private static final class StaleQuotations extends InMemoryQuotationRepository {

        private final InMemoryQuotationRepository delegate;
        private Quotation stale;

        StaleQuotations(InMemoryQuotationRepository delegate, Quotation stale) {
            this.delegate = delegate;
            this.stale = stale;
        }

        @Override
        public Optional<Quotation> findById(QuotationId id) {
            if (stale != null && id.equals(stale.id())) {
                Quotation first = stale;
                stale = null;
                return Optional.of(first);
            }
            return delegate.findById(id);
        }
    }

    /** 輸送要求の最初の読み込みの直後に、ほかの処理を割り込ませる。 */
    private static final class InterleavingRequests extends InMemoryTransportRequestRepository {

        private final InMemoryTransportRequestRepository delegate;
        private Runnable interleaved;

        InterleavingRequests(InMemoryTransportRequestRepository delegate, Runnable interleaved) {
            this.delegate = delegate;
            this.interleaved = interleaved;
        }

        @Override
        public Optional<TransportRequest> findById(TransportRequestId id) {
            Optional<TransportRequest> found = delegate.findById(id);
            if (interleaved != null) {
                Runnable other = interleaved;
                interleaved = null;
                other.run();
            }
            return found;
        }

        @Override
        public void update(TransportRequest transportRequest) {
            delegate.update(transportRequest);
        }
    }
}
