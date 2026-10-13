package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.DuplicateTotpEnrollmentException;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 同じ利用者の同時の 2 つの登録のうち 1 つだけが通り、回復コードは 10 個のままであることを、本物の暗号化・PostgreSQL で確かめる
 * （Bolt 31b、Bolt 30 の議題 2 の①のうち初回の登録の分。T-99・T-100）。先の登録のコミットを止め、後の登録が TOTP の資格の主キーを
 * 待っていることを確かめてから放す。後の登録は主キーの違反で例外になり、自分のトランザクションをロールバックする。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TotpEnrollmentServiceIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    TotpEnrollmentService service;

    @Autowired
    TotpCredentialRepository credentials;

    @Autowired
    RecoveryCodeRepository recoveryCodes;

    @Autowired
    TotpCodeGenerator generator;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    Clock clock;

    @Test
    void 重なった同じ利用者の登録は先の1つだけが通り後は主キーの違反で拒み回復コードは10個() throws Exception {
        UserId user = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        AuthenticatedActor actor = actorOf(user);
        EncryptedSecret secret = started(actor);
        String code = generator.code(user, secret, TotpCredential.stepAt(new UtcInstant(clock.instant())));
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<EnrollmentResult> first = CompletableFuture.supplyAsync(
                    () -> transactionTemplate.execute(status -> {
                        EnrollmentResult result = service.complete(actor, secret, code);
                        firstSaved.countDown();
                        await(releaseFirst);
                        return result;
                    }),
                    executor);
            await(firstSaved);
            CompletableFuture<String> second = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return service.complete(actor, secret, code)
                                    .getClass()
                                    .getSimpleName();
                        } catch (DuplicateTotpEnrollmentException e) {
                            return "Duplicate";
                        }
                    },
                    executor);

            assertThat(second)
                    .as("後の登録は先の登録の主キーを待つ")
                    .failsWithin(Duration.ofMillis(500))
                    .withThrowableOfType(TimeoutException.class);
            releaseFirst.countDown();

            assertThat(first.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join())
                    .isInstanceOf(EnrollmentResult.Enrolled.class);
            assertThat(second.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join())
                    .isEqualTo("Duplicate");
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
        assertThat(recoveryCodes.findUnusedByUserId(user)).hasSize(10);
        assertThat(credentials.findByUserId(user)).isPresent();
    }

    private EncryptedSecret started(AuthenticatedActor actor) {
        EnrollmentStart start = service.begin(actor, "user@example.com", Optional.empty());
        return ((EnrollmentStart.Started) start).secret();
    }

    private AuthenticatedActor actorOf(UserId user) {
        return new AuthenticatedActor(
                user, users.findById(user).orElseThrow().companyId(), EnumSet.of(Role.SHIPPER), "荷主 太郎", "荷主 A");
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
