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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * 先のトランザクションは更新の後にコミットを止め、後のトランザクションがその行ロックを待っていることを確かめてから
 * コミットする。READ COMMITTED では、後の UPDATE は先のコミットの後に WHERE と SET を評価し直す。
 * テストの間でデータを共有しないよう、クラスに @Transactional を付けず、利用者はテストごとに作る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisAuthenticationFactorConcurrencyIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final long BLOCKED_CHECK_MILLIS = 500;
    private static final Instant T0 = Instant.parse("2026-10-11T00:00:00Z");
    private static final LockPolicy POLICY = new LockPolicy();
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

    private UserId userWithFailures(int failures) {
        UserId user = newUser();
        for (int i = 0; i < failures; i++) {
            loginFailures.recordFailure(user, new UtcInstant(T0.plusSeconds(i)), POLICY);
        }
        return user;
    }

    @Test
    void 重なった2つの失敗は両方を数え5回目でロックする() {
        UserId user = userWithFailures(3);
        UtcInstant failedAt = new UtcInstant(T0.plusSeconds(10));

        runOverlapping(
                () -> loginFailures.recordFailure(user, failedAt, POLICY),
                () -> loginFailures.recordFailure(user, failedAt, POLICY));

        LoginFailure failure = loginFailures.findByUserId(user);
        assertThat(failure.consecutiveFailures()).isEqualTo(5);
        assertThat(failure.lockedUntil()).contains(POLICY.lockedUntil(failedAt));
    }

    @Test
    void 五回目と重なった失敗は数えずロックの期限も延ばさない() {
        UserId user = userWithFailures(4);
        UtcInstant fifth = new UtcInstant(T0.plusSeconds(10));
        UtcInstant later = new UtcInstant(T0.plusSeconds(11));

        runOverlapping(
                () -> loginFailures.recordFailure(user, fifth, POLICY),
                () -> loginFailures.recordFailure(user, later, POLICY));

        LoginFailure failure = loginFailures.findByUserId(user);
        assertThat(failure.consecutiveFailures()).isEqualTo(5);
        assertThat(failure.lockedUntil()).contains(POLICY.lockedUntil(fifth));
    }

    @Test
    void 重なった同じ刻みの使用は先の1つだけが通る() {
        UserId user = newUser();
        totpCredentials.add(TotpCredential.enroll(user, new EncryptedSecret("ciphertext"), new UtcInstant(T0)));

        List<Boolean> results =
                runOverlapping(() -> totpCredentials.useStep(user, 100), () -> totpCredentials.useStep(user, 100));

        assertThat(results).containsExactly(true, false);
    }

    @Test
    void 重なった同じ回復コードの消費は先の1つだけが通る() {
        UserId user = newUser();
        List<RecoveryCode> issued = IntStream.rangeClosed(1, RecoveryCode.CODES_PER_USER)
                .mapToObj(i -> RecoveryCode.issue(user, "code-" + i, HASHER, new UtcInstant(T0)))
                .toList();
        transactionTemplate.executeWithoutResult(status -> recoveryCodes.replaceAll(user, issued));
        UtcInstant usedAt = new UtcInstant(T0.plusSeconds(60));

        List<Boolean> results = runOverlapping(
                () -> recoveryCodes.use(user, issued.getFirst().id(), usedAt),
                () -> recoveryCodes.use(user, issued.getFirst().id(), usedAt));

        assertThat(results).containsExactly(true, false);
    }

    /**
     * 先の操作を別のトランザクションで動かしてコミットを止め、後の操作を別のトランザクションで始める。後の操作が
     * 先の行ロックを待っている（終わらない）ことを確かめてから、先をコミットする。結果は先・後の順に返す。
     */
    private <T> List<T> runOverlapping(Supplier<T> first, Supplier<T> second) {
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<T> firstResult = CompletableFuture.supplyAsync(
                    () -> transactionTemplate.execute(status -> {
                        T result = first.get();
                        firstUpdated.countDown();
                        awaitQuietly(releaseFirst);
                        return result;
                    }),
                    executor);
            awaitQuietly(firstUpdated);
            CompletableFuture<T> secondResult =
                    CompletableFuture.supplyAsync(() -> transactionTemplate.execute(status -> second.get()), executor);

            assertThat(secondResult)
                    .as("後の操作は先の行ロックを待つ")
                    .failsWithin(Duration.ofMillis(BLOCKED_CHECK_MILLIS))
                    .withThrowableOfType(TimeoutException.class);
            releaseFirst.countDown();

            return List.of(
                    firstResult.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join(),
                    secondResult.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join());
        } finally {
            releaseFirst.countDown();
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
