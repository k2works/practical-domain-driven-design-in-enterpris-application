package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * TOTP の資格・回復コード・ログインの失敗のリポジトリの契約（Bolt 30）。メモリ上の実装と MyBatis の実装の両方で
 * 同じ振る舞いを確かめる。並行の確かめは PostgreSQL の統合テストで行う。
 */
public abstract class AuthenticationFactorRepositoriesContract {

    /** 偽のハッシュ。接頭辞を付けるだけ（本物の bcrypt は identity.infrastructure）。 */
    protected static final RecoveryCodeHasher HASHER = new RecoveryCodeHasher() {
        @Override
        public String hash(String code) {
            return "hashed:" + code;
        }

        @Override
        public boolean matches(String code, String hash) {
            return hash.equals("hashed:" + code);
        }
    };

    private static final LockPolicy POLICY = new LockPolicy();
    private static final Instant T0 = Instant.parse("2026-10-11T00:00:00Z");

    protected abstract TotpCredentialRepository totpCredentials();

    protected abstract RecoveryCodeRepository recoveryCodes();

    protected abstract LoginFailureRepository loginFailures();

    /** 登録済みの利用者を作る（MyBatis では外部キーのため利用者の行が要る）。 */
    protected abstract UserId newUser();

    protected static UtcInstant at(Duration offset) {
        return new UtcInstant(T0.plus(offset));
    }

    private static List<RecoveryCode> issue(UserId userId, String prefix) {
        return IntStream.rangeClosed(1, RecoveryCode.CODES_PER_USER)
                .mapToObj(i -> RecoveryCode.issue(userId, prefix + i, HASHER, at(Duration.ZERO)))
                .toList();
    }

    @Test
    void 登録したTOTPの資格を利用者で読み出せる() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, new EncryptedSecret("ciphertext"), at(Duration.ZERO)));

        assertThat(totpCredentials().findByUserId(user)).hasValueSatisfying(found -> {
            assertThat(found.secret()).isEqualTo(new EncryptedSecret("ciphertext"));
            assertThat(found.lastUsedStep()).isZero();
            assertThat(found.enrolledAt()).isEqualTo(at(Duration.ZERO));
        });
    }

    @Test
    void 資格のない利用者は見つからず刻みも使えない() {
        UserId user = newUser();

        assertThat(totpCredentials().findByUserId(user)).isEmpty();
        assertThat(totpCredentials().useStep(user, 1)).isFalse();
    }

    @Test
    void 刻みは最後に使った刻みより後のときだけ使える() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, new EncryptedSecret("ciphertext"), at(Duration.ZERO)));

        assertThat(totpCredentials().useStep(user, 100)).isTrue();
        assertThat(totpCredentials().useStep(user, 100)).as("同じ刻みの再利用").isFalse();
        assertThat(totpCredentials().useStep(user, 99)).as("前の刻み").isFalse();
        assertThat(totpCredentials().useStep(user, 101)).isTrue();
        assertThat(totpCredentials().findByUserId(user).orElseThrow().lastUsedStep())
                .isEqualTo(101);
    }

    @Test
    void 負の刻みは失敗へ倒す() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, new EncryptedSecret("ciphertext"), at(Duration.ZERO)));

        assertThatThrownBy(() -> totpCredentials().useStep(user, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 発行した回復コードは未使用のものだけを読み出せ一度だけ使える() {
        UserId user = newUser();
        List<RecoveryCode> issued = issue(user, "code-");
        recoveryCodes().replaceAll(user, issued);
        RecoveryCode first = issued.getFirst();

        assertThat(recoveryCodes().findUnusedByUserId(user)).hasSize(RecoveryCode.CODES_PER_USER);
        assertThat(recoveryCodes().use(first.id(), at(Duration.ofMinutes(1)))).isTrue();
        assertThat(recoveryCodes().use(first.id(), at(Duration.ofMinutes(2))))
                .as("2 回目")
                .isFalse();
        assertThat(recoveryCodes().findUnusedByUserId(user))
                .hasSize(RecoveryCode.CODES_PER_USER - 1)
                .noneMatch(code -> code.id().equals(first.id()))
                .allMatch(code -> code.userId().equals(user) && !code.isUsed());
    }

    @Test
    void 読み出した回復コードは平文で照合できる() {
        UserId user = newUser();
        recoveryCodes().replaceAll(user, issue(user, "code-"));

        assertThat(recoveryCodes().findUnusedByUserId(user))
                .anyMatch(code -> code.matches("code-3", HASHER))
                .noneMatch(code -> code.matches("code-99", HASHER));
    }

    @Test
    void 回復コードを発行し直すと前のコードは使えなくなる() {
        UserId user = newUser();
        List<RecoveryCode> old = issue(user, "old-");
        recoveryCodes().replaceAll(user, old);

        recoveryCodes().replaceAll(user, issue(user, "new-"));

        assertThat(recoveryCodes().findUnusedByUserId(user))
                .hasSize(RecoveryCode.CODES_PER_USER)
                .noneMatch(code -> code.matches("old-1", HASHER));
        assertThat(recoveryCodes().use(old.getFirst().id(), at(Duration.ofMinutes(1))))
                .isFalse();
    }

    @Test
    void 回復コードは10個でなければ発行できない() {
        UserId user = newUser();

        assertThatThrownBy(() ->
                        recoveryCodes().replaceAll(user, issue(user, "code-").subList(0, 9)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 存在しない回復コードは使えない() {
        assertThat(recoveryCodes().use(UUID.randomUUID(), at(Duration.ZERO))).isFalse();
    }

    @Test
    void 失敗の記録のない利用者は0回でロックしていない() {
        LoginFailure failure = loginFailures().findByUserId(newUser());

        assertThat(failure.consecutiveFailures()).isZero();
        assertThat(failure.lockedUntil()).isEmpty();
    }

    @Test
    void 失敗を数え5回目でロックしロック中は数えない() {
        UserId user = newUser();
        LoginFailure failure = null;
        for (int i = 0; i < 4; i++) {
            failure = loginFailures().recordFailure(user, at(Duration.ofSeconds(i)), POLICY);
        }
        assertThat(failure.consecutiveFailures()).isEqualTo(4);
        assertThat(failure.lockedUntil()).isEmpty();

        LoginFailure fifth = loginFailures().recordFailure(user, at(Duration.ofSeconds(4)), POLICY);
        LoginFailure whileLocked = loginFailures().recordFailure(user, at(Duration.ofMinutes(10)), POLICY);

        assertThat(fifth.consecutiveFailures()).isEqualTo(5);
        assertThat(fifth.lockedUntil()).contains(at(Duration.ofSeconds(4).plusMinutes(15)));
        assertThat(whileLocked.consecutiveFailures()).isEqualTo(5);
        assertThat(whileLocked.lockedUntil()).isEqualTo(fifth.lockedUntil());
        assertThat(loginFailures().findByUserId(user).isLocked(at(Duration.ofMinutes(10))))
                .isTrue();
    }

    @Test
    void ロックが解けた後の失敗は1回目から数え直す() {
        UserId user = newUser();
        for (int i = 0; i < 5; i++) {
            loginFailures().recordFailure(user, at(Duration.ofSeconds(i)), POLICY);
        }

        LoginFailure afterLock = loginFailures().recordFailure(user, at(Duration.ofMinutes(20)), POLICY);

        assertThat(afterLock.consecutiveFailures()).isEqualTo(1);
        assertThat(afterLock.lockedUntil()).isEmpty();
    }

    @Test
    void 成功すると回数を0に戻しロックを外す() {
        UserId user = newUser();
        for (int i = 0; i < 3; i++) {
            loginFailures().recordFailure(user, at(Duration.ofSeconds(i)), POLICY);
        }

        loginFailures().recordSuccess(user, at(Duration.ofMinutes(1)));

        assertThat(loginFailures().findByUserId(user).consecutiveFailures()).isZero();
    }
}
