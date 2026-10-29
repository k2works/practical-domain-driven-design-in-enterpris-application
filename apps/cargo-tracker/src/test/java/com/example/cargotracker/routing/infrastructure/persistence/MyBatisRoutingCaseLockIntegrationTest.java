package com.example.cargotracker.routing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.OverlappingTransactions;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DE-05 の listener が案件の行を押さえて読むと、評価の保存（案件の行を書く）がそのコミットまで待つことを PostgreSQL で確かめる
 * （DE-05 と DE-06 の届く順 G。US-07 AC5。Bolt 37e2）。後の操作が先の接続を待っていることは {@code pg_blocking_pids} で確かめる。
 * テストの間でデータを共有しないよう、クラスに @Transactional を付けず、案件番号は 2095 年を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisRoutingCaseLockIntegrationTest {

    @Autowired
    RoutingCaseRepository repository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void 案件の行を押さえて読んでいる間は案件の保存がそのコミットまで待つ() {
        RoutingCase routingCase = open(1);
        transactionTemplate.executeWithoutResult(status -> repository.save(routingCase));

        List<Long> versions = OverlappingTransactions.run(
                transactionTemplate,
                () -> repository
                        .findByNumberForRouteAssignment(routingCase.number())
                        .orElseThrow()
                        .aggregateVersion(),
                () -> {
                    RoutingCase loaded =
                            repository.findByNumber(routingCase.number()).orElseThrow();
                    repository.update(loaded, null);
                    return loaded.aggregateVersion();
                });

        assertThat(versions).containsExactly(0L, 0L);
        assertThat(repository.findByNumber(routingCase.number()).orElseThrow().aggregateVersion())
                .isEqualTo(1L);
    }

    @Test
    void 案件の保存がコミットする前に押さえて読むとそのコミットを待って保存の後の案件を読む() {
        // 届く順 G'（評価の保存が先に案件の行を書き、DE-05 の listener が後から押さえて読む）。押さえる前に読むと保存の前の案件になる
        RoutingCase routingCase = open(2);
        transactionTemplate.executeWithoutResult(status -> repository.save(routingCase));

        List<Long> versions = OverlappingTransactions.run(
                transactionTemplate,
                () -> {
                    RoutingCase loaded =
                            repository.findByNumber(routingCase.number()).orElseThrow();
                    repository.update(loaded, null);
                    return loaded.aggregateVersion();
                },
                () -> repository
                        .findByNumberForRouteAssignment(routingCase.number())
                        .orElseThrow()
                        .aggregateVersion());

        assertThat(versions).containsExactly(0L, 1L);
    }

    @Test
    void 案件がなければ押さえずに空を返す() {
        Optional<RoutingCase> found = transactionTemplate.execute(
                status -> repository.findByNumberForRouteAssignment(new RoutingCaseNumber(2095, 9999)));

        assertThat(found).isEmpty();
    }

    @Test
    void 呼び手のトランザクションの外では押さえて読まずに例外にする() {
        // 外で呼ぶと文の終わりでロックが外れ、直列にならないことに気づけない（開発レビュー）
        RoutingCase routingCase = open(3);
        transactionTemplate.executeWithoutResult(status -> repository.save(routingCase));

        RoutingCaseNumber number = routingCase.number();

        assertThatThrownBy(() -> repository.findByNumberForRouteAssignment(number))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private static RoutingCase open(int sequence) {
        return RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                new RoutingCaseNumber(2095, sequence),
                UUID.randomUUID(),
                "TR-2095-0001",
                1,
                UUID.randomUUID(),
                List.of(),
                new RouteSpecification(
                        new Location("JPTYO"), new Location("NLRTM"), at("2095-11-02T00:00:00Z"), "GENERAL"),
                at("2095-10-06T02:00:00Z"),
                at("2095-10-08T09:00:00Z"),
                UUID.randomUUID(),
                new CompanyId(UUID.randomUUID()));
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
