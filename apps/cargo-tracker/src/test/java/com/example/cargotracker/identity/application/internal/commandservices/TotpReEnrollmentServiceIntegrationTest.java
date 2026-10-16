package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.EnrollingUser;
import com.example.cargotracker.identity.domain.model.valueobjects.SessionId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 認証アプリの再登録を、本物の暗号化・PostgreSQL で確かめる（Bolt 33b、US-18 AC9、SEC-10、IA-INV-10・11）。置き換えた後は古い秘密の
 * コードと古い回復コードが通らないこと、同時の操作は先の置き換えの確定を待って 1 つだけが通ること（T-100。Bolt 31b の宿題の
 * 「回復コードの発行し直しの競合」）を確かめる。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TotpReEnrollmentServiceIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final SessionId SESSION = new SessionId("re-enrolling-session");

    @Autowired
    TotpReEnrollmentService service;

    @Autowired
    TotpEnrollmentService enrollment;

    @Autowired
    TotpVerificationService totp;

    @Autowired
    RecoveryCodeVerificationService recovery;

    @Autowired
    TotpCredentialRepository credentials;

    @Autowired
    RecoveryCodeRepository recoveryCodes;

    @Autowired
    TotpSecretIssuer issuer;

    @Autowired
    RecoveryCodeGenerator recoveryCodeGenerator;

    @Autowired
    RecoveryCodeHasher hasher;

    @Autowired
    TotpCodeGenerator generator;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    private record Enrolled(UserId user, CompanyId company, EncryptedSecret secret, List<String> recoveryCodes) {}

    /** 初回の登録を済ませた利用者（古い秘密と古い回復コード）。 */
    private Enrolled enrolledUser() {
        UserId user = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        CompanyId company = users.findById(user).orElseThrow().companyId();
        EnrollingUser actor = new EnrollingUser(user, company, EmailAddress.of("user@example.com"));
        EncryptedSecret secret = ((EnrollmentStart.Started) enrollment.begin(actor, Optional.empty())).secret();
        // 登録のコードには 1 つ前の刻みを使い、今の刻みを再登録の確かめに残す
        EnrollmentResult enrolled = enrollment.complete(actor, secret, codeAt(user, secret, -1));
        return new Enrolled(user, company, secret, ((EnrollmentResult.Enrolled) enrolled).recoveryCodes());
    }

    private String codeAt(UserId user, EncryptedSecret secret, long offset) {
        return generator.code(user, secret, TotpCredential.stepAt(new UtcInstant(clock.instant())) + offset);
    }

    private EncryptedSecret begin(UserId user) {
        return ((ReEnrollmentStart.Started) service.begin(user, Optional.empty())).secret();
    }

    private ReEnrollmentResult complete(UserId user, EncryptedSecret secret) {
        return service.complete(user, secret, codeAt(user, secret, 0), new UtcInstant(clock.instant()), SESSION);
    }

    @Test
    void 登録し直すと古い秘密のコードと古い回復コードは通らず新しい秘密のコードと新しい回復コードが通る() {
        Enrolled before = enrolledUser();
        EncryptedSecret renewed = begin(before.user());

        ReEnrollmentResult result = complete(before.user(), renewed);

        assertThat(result).isInstanceOf(ReEnrollmentResult.ReEnrolled.class);
        List<String> newCodes = ((ReEnrollmentResult.ReEnrolled) result).recoveryCodes();
        assertThat(recoveryCodes.findUnusedByUserId(before.user())).hasSize(10);
        assertThat(totp.verify(before.user(), codeAt(before.user(), before.secret(), 1)))
                .as("古い秘密の次の刻みのコード")
                .isNotEqualTo(TotpVerification.ACCEPTED);
        assertThat(recovery.verify(
                        before.user(), before.company(), before.recoveryCodes().getFirst()))
                .as("古い回復コード")
                .isEqualTo(RecoveryCodeVerification.REJECTED);
        assertThat(totp.verify(before.user(), codeAt(before.user(), renewed, 1)))
                .as("新しい秘密の次の刻みのコード")
                .isEqualTo(TotpVerification.ACCEPTED);
        assertThat(recovery.verify(before.user(), before.company(), newCodes.getFirst()))
                .as("新しい回復コード")
                .isEqualTo(RecoveryCodeVerification.ACCEPTED);
    }

    @Test
    void 先の再登録の置き換えが確定するまで後の再登録は待ちほかの画面で登録し直したとして置き換えない() {
        Enrolled before = enrolledUser();
        EncryptedSecret first = begin(before.user());
        EncryptedSecret second = issuer.issue(before.user());
        TotpCredential read = credentials.findByUserId(before.user()).orElseThrow();

        List<String> firstCodes = recoveryCodeGenerator.generate();

        List<Object> results = whileFirstHoldsTheCredential(
                () -> {
                    UtcInstant now = new UtcInstant(clock.instant());
                    boolean replaced = credentials.replace(read.reEnroll(first, 0, now), before.secret());
                    recoveryCodes.replaceAll(
                            before.user(),
                            firstCodes.stream()
                                    .map(plain -> RecoveryCode.issue(before.user(), plain, hasher, now))
                                    .toList());
                    return replaced;
                },
                () -> complete(before.user(), second));

        assertThat(results).containsExactly(true, new ReEnrollmentResult.Superseded());
        assertThat(credentials.findByUserId(before.user()).orElseThrow().secret())
                .isEqualTo(first);
        assertThat(recovery.verify(before.user(), before.company(), firstCodes.getFirst()))
                .as("先の再登録が示した回復コードは、後の再登録で無効にならない")
                .isEqualTo(RecoveryCodeVerification.ACCEPTED);
    }

    @Test
    void 回復コードの発行し直しが確定するまで古い回復コードのログインは待ち確定の後は通らない() {
        Enrolled before = enrolledUser();
        EncryptedSecret renewed = begin(before.user());
        TotpCredential read = credentials.findByUserId(before.user()).orElseThrow();
        String oldCode = before.recoveryCodes().getFirst();

        List<Object> results = whileFirstHoldsTheCredential(
                () -> {
                    boolean replaced = credentials.replace(
                            read.reEnroll(renewed, 0, new UtcInstant(clock.instant())), before.secret());
                    UtcInstant now = new UtcInstant(clock.instant());
                    recoveryCodes.replaceAll(
                            before.user(),
                            recoveryCodeGenerator.generate().stream()
                                    .map(plain -> RecoveryCode.issue(before.user(), plain, hasher, now))
                                    .toList());
                    return replaced;
                },
                () -> recovery.verify(before.user(), before.company(), oldCode));

        assertThat(results).containsExactly(true, RecoveryCodeVerification.REJECTED);
    }

    /**
     * 先の操作をトランザクションの中で行い、確定を止めてから後の操作を始める。後の操作が待っていることを確かめてから、先を確定させる
     * （T-100）。
     */
    private List<Object> whileFirstHoldsTheCredential(Supplier<Object> first, Supplier<Object> second) {
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Object> firstResult = CompletableFuture.supplyAsync(
                    () -> transactionTemplate.execute(status -> {
                        Object result = first.get();
                        firstDone.countDown();
                        await(releaseFirst);
                        return result;
                    }),
                    executor);
            await(firstDone);
            CompletableFuture<Object> secondResult = CompletableFuture.supplyAsync(second, executor);

            // 後の操作が先の行ロックを待っていることを、時間ではなく PostgreSQL の待ちで確かめる（bcrypt の時間で空振りしない）
            awaitLockWaiter();
            assertThat(secondResult).as("後の操作は先の確定を待つ").isNotDone();
            releaseFirst.countDown();
            return List.of(
                    firstResult.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join(),
                    secondResult.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    /** ほかの接続が行ロックを待つまで待つ。 */
    private void awaitLockWaiter() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                            + " AND datname = current_database()",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("後の操作が行ロックを待ちませんでした");
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
