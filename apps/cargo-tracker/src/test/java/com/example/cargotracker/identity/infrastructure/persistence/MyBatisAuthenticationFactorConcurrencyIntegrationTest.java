package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailure;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 失敗の加算・刻みの使用・回復コードの消費が、別々のトランザクションでの同時実行でも 1 文の条件付きの更新で
 * 正しく判定されることを確かめる（Bolt 30、ADR-011 の「更新の原子性」）。
 * READ COMMITTED では、後の UPDATE は先の行ロックを待ち、WHERE を評価し直す。
 * テストの間でデータを共有しないよう、クラスに @Transactional を付けず、利用者はテストごとに作る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisAuthenticationFactorConcurrencyIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final Instant T0 = Instant.parse("2026-10-11T00:00:00Z");
    private static final RecoveryCodeHasher HASHER = new RecoveryCodeHasher() {
        @Override
        public String hash(String code) {
            return "hashed:" + code;
        }

        @Override
        public boolean matches(String code, String hash) {
            return hash.equals("hashed:" + code);
        }
    };

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    RecoveryCodeRepository recoveryCodes;

    @Autowired
    LoginFailureRepository loginFailures;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transactionTemplate;

    private UserId newUser() {
        return transactionTemplate.execute(
                status -> MyBatisAuthenticationFactorRepositoriesIntegrationTest.AuthenticationFactorFixtures.newUser(
                        companies, users));
    }

    @Test
    void 同時の2つの失敗は両方を数え5回目でロックする() throws Exception {
        UserId user = newUser();
        LockPolicy policy = new LockPolicy();
        for (int i = 0; i < 3; i++) {
            loginFailures.recordFailure(user, new UtcInstant(T0.plusSeconds(i)), policy);
        }
        UtcInstant failedAt = new UtcInstant(T0.plusSeconds(10));

        runTogether(
                () -> loginFailures.recordFailure(user, failedAt, policy),
                () -> loginFailures.recordFailure(user, failedAt, policy));

        LoginFailure failure = loginFailures.findByUserId(user);
        assertThat(failure.consecutiveFailures()).isEqualTo(5);
        assertThat(failure.lockedUntil()).contains(policy.lockedUntil(failedAt));
    }

    @Test
    void 同じ刻みの同時の使用は1つだけが通る() throws Exception {
        UserId user = newUser();
        totpCredentials.add(TotpCredential.enroll(user, new EncryptedSecret("ciphertext"), new UtcInstant(T0)));

        List<Boolean> results =
                runTogether(() -> totpCredentials.useStep(user, 100), () -> totpCredentials.useStep(user, 100));

        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void 同じ回復コードの同時の消費は1つだけが通る() throws Exception {
        UserId user = newUser();
        List<RecoveryCode> issued = IntStream.rangeClosed(1, RecoveryCode.CODES_PER_USER)
                .mapToObj(i -> RecoveryCode.issue(user, "code-" + i, HASHER, new UtcInstant(T0)))
                .toList();
        transactionTemplate.executeWithoutResult(status -> recoveryCodes.replaceAll(user, issued));
        UtcInstant usedAt = new UtcInstant(T0.plusSeconds(60));

        List<Boolean> results = runTogether(
                () -> recoveryCodes.use(issued.getFirst().id(), usedAt),
                () -> recoveryCodes.use(issued.getFirst().id(), usedAt));

        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    /** 2 つの操作を、ラッチでそろえてから別々のトランザクションで同時に動かす。 */
    @SafeVarargs
    private <T> List<T> runTogether(Supplier<T>... operations) throws Exception {
        CountDownLatch ready = new CountDownLatch(operations.length);
        ExecutorService executor = Executors.newFixedThreadPool(operations.length);
        try {
            List<CompletableFuture<T>> futures = Arrays.stream(operations)
                    .map(operation -> CompletableFuture.supplyAsync(
                            () -> {
                                ready.countDown();
                                awaitQuietly(ready);
                                return transactionTemplate.execute(status -> operation.get());
                            },
                            executor))
                    .toList();
            return futures.stream()
                    .map(future ->
                            future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join())
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
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
