package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.application.internal.eventhandlers.QuotationApprovedByShipperEventHandler;
import com.example.cargotracker.quotation.application.internal.eventhandlers.QuotationPresentedForReapprovalEventHandler;
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationPresentedForReapproval;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 再見積りの提示で輸送要求を荷主承認待ちへ戻す DE-24 の listener が、見積りを読んだ後に荷主の再承認と DE-04 の書き込みが別の
 * トランザクションでコミットされても、楽観ロックでぶつかって読み直し、戻さずに予約待ちのままにすることを PostgreSQL 18 で確かめる
 * （ADR-018 の差し込みの順 1。Bolt 35b2）。守りが頼る次の 3 つは、メモリの実装では写せない: READ COMMITTED で同じトランザクションの
 * 中の読み直しが相手のコミットを読むこと、版の合わない UPDATE が 0 行になること、0 行の UPDATE の後に MyBatis の 1 次キャッシュが古い
 * 見積りを返さないこと。テストの間でデータを共有しないよう、クラスに @Transactional を付けず、業務番号は 2087 年を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReapprovalOrderingConcurrentIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2087-01-05T03:00:00Z"));
    private static final UtcInstant EXPIRES = QuotationFixture.EXPIRES_AT;

    @Autowired
    TransportRequestRepository transportRequests;

    @Autowired
    QuotationRepository quotations;

    @Autowired
    TransactionTemplate transactionTemplate;

    private final UserId staff = new UserId(UUID.randomUUID());
    private final UserId shipper = new UserId(UUID.randomUUID());

    @Test
    void DE24が見積りを読んだ後に再承認とDE04がコミットしてもDE24は読み直して戻さず輸送要求は予約待ちのまま() {
        TransportRequestId id = readyToBookTransportRequest();
        Quotation old = approvedQuotation1(id);
        Quotation next = requotedAndPresented(old);
        long version = versionOf(id);
        CountDownLatch quotationRead = new CountDownLatch(1);
        CountDownLatch approvalCommitted = new CountDownLatch(1);
        AtomicInteger quotationReads = new AtomicInteger();
        QuotationPresentedForReapprovalEventHandler reapproval = new QuotationPresentedForReapprovalEventHandler(
                transportRequests, pausingAfterFirstRead(quotations, quotationRead, approvalCommitted, quotationReads));
        QuotationApprovedByShipperEventHandler approval =
                new QuotationApprovedByShipperEventHandler(transportRequests, quotations);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> returning = CompletableFuture.runAsync(
                    () -> transactionTemplate.executeWithoutResult(_ -> reapproval.on(
                            new QuotationPresentedForReapproval(next.id().value(), 2, id.value(), 1, EXPIRES))),
                    executor);
            await(quotationRead);
            transactionTemplate.executeWithoutResult(_ -> {
                Quotation found = quotations.findById(next.id()).orElseThrow();
                found.approveByShipper(shipper, EXPIRES);
                quotations.update(found);
            });
            transactionTemplate.executeWithoutResult(_ -> approval.on(new QuotationApprovedByShipper(
                    next.id().value(), 2, id.value(), 1, "RC-2026-0001", 1, shipper.value(), EXPIRES)));
            approvalCommitted.countDown();

            returning.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join();

            assertThat(transportRequests.findById(id).orElseThrow().status())
                    .isEqualTo(TransportRequestStatus.READY_TO_BOOK);
            assertThat(versionOf(id))
                    .as("DE-04 の書き込みの 1 回だけ（DE-24 はぶつかって戻さなかった）")
                    .isEqualTo(version + 1);
            assertThat(quotationReads).as("DE-24 は競合の後に見積りを読み直した").hasValue(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private TransportRequestId readyToBookTransportRequest() {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2087, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                staff,
                NOW);
        transactionTemplate.executeWithoutResult(_ -> transportRequests.save(request));
        transactionTemplate.executeWithoutResult(_ -> {
            TransportRequest found = transportRequests.findById(request.id()).orElseThrow();
            found.approve(1, staff, "根拠", NOW);
            found.markQuotationPresented(1);
            found.markRoutingRequested(1);
            found.markAwaitingApproval(1);
            found.markReadyToBook(1);
            transportRequests.update(found);
        });
        return request.id();
    }

    /** 荷主が承認した見積り 1（有効期限 2099-10-08T09:00:00Z）。 */
    private Quotation approvedQuotation1(TransportRequestId id) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), id, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), NOW);
        quotation.presentInternally(staff, NOW);
        quotation.requestRouteDesign(shipper, NOW);
        quotation.assignRoute(QuotationFixture.assignedRoute(), NOW);
        quotation.approveByShipper(shipper, NOW);
        transactionTemplate.executeWithoutResult(_ -> quotations.save(quotation));
        return quotation;
    }

    /** 見積り 1 を有効期限の時刻に再見積りし、見積り 2 を社内承認して提示する（荷主承認待ち）。 */
    private Quotation requotedAndPresented(Quotation old) {
        Quotation next = Quotation.create(new QuotationId(UUID.randomUUID()), old.transportRequestId(), 1, 2);
        next.calculate(
                new QuotationInput(
                        QuotationFixture.completeInput().lines(),
                        QuotationFixture.completeInput().currency(),
                        new UtcInstant(EXPIRES.instant().plusSeconds(86_400)),
                        List.of(),
                        new UtcInstant(EXPIRES.instant().plusSeconds(172_800)),
                        new UtcInstant(EXPIRES.instant().plusSeconds(259_200))),
                EXPIRES);
        transactionTemplate.executeWithoutResult(_ -> {
            Quotation stored = quotations.findById(old.id()).orElseThrow();
            stored.requoteInto(next, EXPIRES);
            quotations.update(stored);
            quotations.save(next);
        });
        transactionTemplate.executeWithoutResult(_ -> {
            Quotation saved = quotations.findById(next.id()).orElseThrow();
            saved.presentInternally(staff, EXPIRES);
            quotations.update(saved);
        });
        Quotation presented = quotations.findById(next.id()).orElseThrow();
        assertThat(presented.status()).as("前提").isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        return presented;
    }

    private long versionOf(TransportRequestId id) {
        return transportRequests.findById(id).orElseThrow().aggregateVersion();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("DE-24 の listener が見積りを読みませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** 見積りの最初の読み込みの後に、再承認と DE-04 のコミットを待つリポジトリ。読み直しは待たない。読んだ回数を数える。 */
    private static QuotationRepository pausingAfterFirstRead(
            QuotationRepository delegate, CountDownLatch read, CountDownLatch resume, AtomicInteger reads) {
        AtomicBoolean paused = new AtomicBoolean();
        return (QuotationRepository) Proxy.newProxyInstance(
                QuotationRepository.class.getClassLoader(),
                new Class<?>[] {QuotationRepository.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (method.getName().equals("findById")) {
                        reads.incrementAndGet();
                        if (paused.compareAndSet(false, true)) {
                            read.countDown();
                            if (!resume.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("再承認のコミットを待てませんでした");
                            }
                        }
                    }
                    return result;
                });
    }
}
