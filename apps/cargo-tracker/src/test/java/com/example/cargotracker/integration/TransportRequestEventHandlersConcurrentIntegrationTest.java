package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.application.internal.eventhandlers.QuotationPresentedEventHandler;
import com.example.cargotracker.quotation.application.internal.eventhandlers.RouteDesignRequestedEventHandler;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DE-03 と DE-16 の listener が同じ輸送要求を並行して更新しても、どちらも失敗せず、どちらの更新も反映されることを PostgreSQL 18 で
 * 確かめる（Bolt 22 の割り込み。負荷が高いときに RouteDesignRequestedRoutingIntegrationTest が楽観ロックの競合で落ちた）。
 * listener は本番と同じく別々のトランザクションで動かし、ラッチで両方が同じ集約の版を読み込んでから更新させる。
 * テストの間でデータを共有しないよう、クラスに @Transactional を付けず、業務番号は 2086 年を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransportRequestEventHandlersConcurrentIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2086-01-05T03:00:00Z"));

    @Autowired
    TransportRequestRepository repository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void 見積りの提示と詳細経路設計の依頼の配信が同時に輸送要求を更新しても両方が完了し経路設計中になる() {
        TransportRequestId id = quotingTransportRequest();
        CountDownLatch bothLoaded = new CountDownLatch(2);
        QuotationPresentedEventHandler presentation =
                new QuotationPresentedEventHandler(loadingTogether(repository, bothLoaded));
        RouteDesignRequestedEventHandler routeDesign =
                new RouteDesignRequestedEventHandler(loadingTogether(repository, bothLoaded));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Void>> deliveries = List.of(
                    inTransaction(
                            executor,
                            () -> presentation.on(new QuotationPresented(
                                    UUID.randomUUID(), 1, id.value(), 1, NOW, List.of(), NOW, NOW, NOW))),
                    inTransaction(
                            executor,
                            () -> routeDesign.on(new RouteDesignRequested(
                                    UUID.randomUUID(),
                                    1,
                                    id.value(),
                                    1,
                                    List.of(),
                                    NOW,
                                    NOW,
                                    NOW,
                                    UUID.randomUUID(),
                                    NOW))));

            deliveries.forEach(delivery ->
                    delivery.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join());

            assertThat(repository.findById(id).orElseThrow().status()).isEqualTo(TransportRequestStatus.ROUTING);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 審査を確定して見積り作成中にした輸送要求を保存する。 */
    private TransportRequestId quotingTransportRequest() {
        UserId staff = new UserId(UUID.randomUUID());
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2086, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                staff,
                NOW);
        transactionTemplate.executeWithoutResult(_ -> repository.save(request));
        transactionTemplate.executeWithoutResult(_ -> {
            TransportRequest found = repository.findById(request.id()).orElseThrow();
            found.approve(1, staff, "根拠", NOW);
            repository.update(found);
        });
        return request.id();
    }

    /** listener の配信と同じく、新しいトランザクションの中で動かす（@ApplicationModuleListener）。 */
    private CompletableFuture<Void> inTransaction(ExecutorService executor, Runnable delivery) {
        return CompletableFuture.runAsync(
                () -> transactionTemplate.executeWithoutResult(_ -> delivery.run()), executor);
    }

    /** 最初の読み込みの後に、もう一方の listener も読み込むのを待つリポジトリ。読み直しは待たない。 */
    private static TransportRequestRepository loadingTogether(
            TransportRequestRepository delegate, CountDownLatch bothLoaded) {
        AtomicBoolean loaded = new AtomicBoolean();
        return (TransportRequestRepository) Proxy.newProxyInstance(
                TransportRequestRepository.class.getClassLoader(),
                new Class<?>[] {TransportRequestRepository.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (method.getName().equals("findById") && loaded.compareAndSet(false, true)) {
                        bothLoaded.countDown();
                        if (!bothLoaded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("2 つの listener の読み込みがそろいませんでした");
                        }
                    }
                    return result;
                });
    }
}
