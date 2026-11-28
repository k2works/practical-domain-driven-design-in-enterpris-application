package com.example.cargotracker.quotation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 楽観ロックを、別々のトランザクションでの本当の同時実行で確かめる（Bolt 5 レビュー R-04、ARCH-HO-01）。
 * 2 つのトランザクションが同じ集約の版を読んだ後に、ラッチでそろえて同時に更新する。
 * READ COMMITTED では、後の UPDATE は先の行ロックを待ち、WHERE を評価し直して 0 件になる。
 * テストの間でデータを共有しないよう、クラスに @Transactional を付けず、業務番号は 2084 年を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisTransportRequestConcurrentReviewIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final UserId REVIEWER = new UserId(UUID.randomUUID());
    private static final UtcInstant DECIDED_AT = new UtcInstant(Instant.parse("2084-01-05T03:00:00Z"));

    @Autowired
    TransportRequestRepository repository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void 同じ版を2つのトランザクションで同時に審査すると1つだけが成功しもう1つは競合で失敗する() throws Exception {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2084, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2084-01-05T01:00:00Z")));
        transactionTemplate.executeWithoutResult(status -> repository.save(request));

        CountDownLatch bothLoaded = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<String>> results = List.of(
                    review(executor, request.id(), bothLoaded, true),
                    review(executor, request.id(), bothLoaded, false));

            List<String> outcomes = results.stream()
                    .map(future ->
                            future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join())
                    .toList();

            assertThat(outcomes).containsExactlyInAnyOrder("成功", "競合");
            assertThat(repository.findById(request.id()).orElseThrow().reviewRecords())
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 別のトランザクションで読み込み、もう一方も読み込むのを待ってから、確定か差戻しで更新する。 */
    private CompletableFuture<String> review(
            ExecutorService executor, TransportRequestId id, CountDownLatch bothLoaded, boolean approve) {
        return CompletableFuture.supplyAsync(
                () -> transactionTemplate.execute(status -> {
                    TransportRequest loaded = repository.findById(id).orElseThrow();
                    bothLoaded.countDown();
                    awaitQuietly(bothLoaded);
                    if (approve) {
                        loaded.approve(1, REVIEWER, "確定する", DECIDED_AT);
                    } else {
                        loaded.sendBack(1, REVIEWER, "差し戻す", null, DECIDED_AT);
                    }
                    try {
                        repository.update(loaded);
                        return "成功";
                    } catch (ConcurrentTransportRequestUpdateException _) {
                        status.setRollbackOnly();
                        return "競合";
                    }
                }),
                executor);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("2 つのトランザクションがそろいませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
