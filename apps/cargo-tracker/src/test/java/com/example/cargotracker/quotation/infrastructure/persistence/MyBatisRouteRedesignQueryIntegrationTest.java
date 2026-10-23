package com.example.cargotracker.quotation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 経路版で見積りを引く照会を、同じトランザクションで照会し直したときに、ほかのトランザクションが後からコミットした見積りが当たることを
 * PostgreSQL で確かめる（再設計要の伝達の届く順 C。US-07 AC5。Bolt 37e の終了報告の承認の場の指摘）。MyBatis はセッションの中で同じ
 * 引数の select の 2 度目をキャッシュから返すので、照会はキャッシュを使わない（{@code flushCache}）。READ COMMITTED では 2 度目の照会が
 * 相手のコミットを読む。テストの間でデータを共有しないよう、クラスに @Transactional を付けず、業務番号は 2094 年を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisRouteRedesignQueryIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final String CASE = "RC-2094-0001";
    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());

    @Autowired
    QuotationRepository repository;

    @Autowired
    TransportRequestRepository transportRequests;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void 同じトランザクションで照会し直すとほかのトランザクションが後からコミットした見積りが当たる() {
        QuotationId first = transactionTemplate.execute(status -> awaitingApproval(1));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            List<List<QuotationId>> queried = transactionTemplate.execute(status -> {
                List<QuotationId> before = repository.findIdsByAssignedRoute(CASE, 1);
                QuotationId second = CompletableFuture.supplyAsync(
                                () -> transactionTemplate.execute(inner -> awaitingApproval(2)), executor)
                        .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join();
                List<QuotationId> after = repository.findIdsByAssignedRoute(CASE, 1);
                return List.of(before, after, List.of(second));
            });

            assertThat(queried.get(0)).containsExactly(first);
            assertThat(queried.get(1))
                    .containsExactlyInAnyOrder(first, queried.get(2).getFirst());
        } finally {
            executor.shutdownNow();
        }
    }

    /** 輸送要求を作り、経路版を割り当てた荷主承認待ちの見積りを保存する。 */
    private QuotationId awaitingApproval(int sequence) {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2094, sequence),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                at("2085-01-05T01:00:00Z"));
        transportRequests.save(request);
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2085-01-05T04:00:00Z"));
        quotation.presentInternally(STAFF, at("2085-01-05T04:30:00Z"));
        quotation.requestRouteDesign(SHIPPER_USER, at("2085-01-06T02:00:00Z"));
        quotation.assignRoute(route(), at("2085-01-07T05:00:00Z"));
        quotation.clearDomainEvents();
        repository.save(quotation);
        return quotation.id();
    }

    private static AssignedRoute route() {
        return new AssignedRoute(
                CASE,
                1,
                at("2085-01-07T05:00:00Z"),
                List.of(new AssignedRouteLeg(
                        "V-201",
                        new Location("JPTYO"),
                        new Location("NLRTM"),
                        at("2099-10-10T00:00:00Z"),
                        at("2099-10-31T00:00:00Z"))));
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
