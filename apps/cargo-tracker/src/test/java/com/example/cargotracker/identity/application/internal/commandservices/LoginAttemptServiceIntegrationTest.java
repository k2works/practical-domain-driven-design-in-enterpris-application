package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.OverlappingTransactions;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ログインの試行のサービスの同時の失敗を、本物の PostgreSQL で確かめる（Bolt 32、IA-INV-03・IA-INV-08、Bolt 30 の議題 2 の③）。
 * 最初の失敗の作成の競合、同時の失敗の両方の監査記録、同時の 5 回目でロックの監査が 1 行だけになること（T-100）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LoginAttemptServiceIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    LoginAttemptService service;

    @Autowired
    LoginFailureRepository failures;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private UserId newUser() {
        return transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
    }

    private CompanyId companyOf(UserId user) {
        return users.findById(user).orElseThrow().companyId();
    }

    private int auditCount(UserId user, String action) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE actor_user_id = ? AND action = ?",
                Integer.class,
                user.value(),
                action);
    }

    @Test
    void 同じ利用者の最初の失敗が同時に届いても両方を数え両方の監査記録を書く() throws Exception {
        // 議題 2 の③: 記録の作成の一意制約の違反をトランザクションの外で受ける
        UserId user = newUser();
        CompanyId company = companyOf(user);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Void>> both = List.of(
                    CompletableFuture.runAsync(
                            () -> {
                                await(start);
                                service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
                            },
                            executor),
                    CompletableFuture.runAsync(
                            () -> {
                                await(start);
                                service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
                            },
                            executor));
            start.countDown();
            CompletableFuture.allOf(both.toArray(CompletableFuture[]::new)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures.findByUserId(user).consecutiveFailures()).isEqualTo(2);
        assertThat(auditCount(user, "LOGIN_FAILED")).isEqualTo(2);
    }

    @Test
    void 重なった5回目の失敗はロックの監査記録を1行だけ書き後の失敗は理由LOCKEDで数えない() {
        UserId user = newUser();
        CompanyId company = companyOf(user);
        for (int i = 0; i < 4; i++) {
            service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
        }

        OverlappingTransactions.run(
                transactionTemplate,
                () -> {
                    service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
                    return Boolean.TRUE;
                },
                () -> {
                    service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
                    return Boolean.TRUE;
                });

        assertThat(failures.findByUserId(user).consecutiveFailures()).isEqualTo(5);
        assertThat(auditCount(user, "ACCOUNT_LOCKED")).as("この失敗でロックしたときだけ").isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT reason FROM identity.audit_record WHERE actor_user_id = ? AND action = 'LOGIN_FAILED'",
                        String.class,
                        user.value()))
                .containsExactlyInAnyOrder(
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "LOCKED");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("同時の操作がそろいませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
