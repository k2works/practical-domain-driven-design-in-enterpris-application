package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.OverlappingTransactions;
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
import java.util.List;
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
        return transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
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

    /** 先の操作のコミットを止め、後の操作が待っていることを確かめてから放す（T-100）。結果は先・後の順。 */
    private <T> List<T> runOverlapping(Supplier<T> first, Supplier<T> second) {
        return OverlappingTransactions.run(transactionTemplate, first, second);
    }
}
