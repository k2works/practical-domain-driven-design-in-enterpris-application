package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.NormalizedRecoveryCode;
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
    private static final EncryptedSecret SECRET = new EncryptedSecret("ciphertext");
    private static final Instant T0 = Instant.parse("2026-10-11T00:00:00Z");

    protected abstract TotpCredentialRepository totpCredentials();

    protected abstract RecoveryCodeRepository recoveryCodes();

    protected abstract LoginFailureRepository loginFailures();

    /** 登録済みの利用者を作る（MyBatis では外部キーのため利用者の行が要る）。 */
    protected abstract UserId newUser();

    /** 登録していない利用者（存在しないメールアドレスの失敗に当たる）。 */
    protected abstract UserId unknownUser();

    protected static UtcInstant at(Duration offset) {
        return new UtcInstant(T0.plus(offset));
    }

    /** 回復コードの形（16 進 4 文字・ハイフン・4 文字）の平文。照合は正規化した値だけを受ける（Bolt 33d）。 */
    private static String code(String prefix, int i) {
        return prefix + "-" + String.format("%04d", i);
    }

    private static NormalizedRecoveryCode normalized(String value) {
        return RecoveryCode.normalize(value).orElseThrow();
    }

    private static List<RecoveryCode> issue(UserId userId, String prefix) {
        return IntStream.rangeClosed(1, RecoveryCode.CODES_PER_USER)
                .mapToObj(i -> RecoveryCode.issue(userId, code(prefix, i), HASHER, at(Duration.ZERO)))
                .toList();
    }

    @Test
    void 同じ利用者の2つ目のTOTPの資格は登録済みとして拒む() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, new EncryptedSecret("ciphertext"), at(Duration.ZERO)));
        TotpCredential second = TotpCredential.enroll(user, new EncryptedSecret("other"), at(Duration.ZERO));

        TotpCredentialRepository repository = totpCredentials();

        assertThatThrownBy(() -> repository.add(second)).isInstanceOf(DuplicateTotpEnrollmentException.class);
        assertThat(totpCredentials().findByUserId(user))
                .hasValueSatisfying(found -> assertThat(found.secret()).isEqualTo(new EncryptedSecret("ciphertext")));
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
        assertThat(totpCredentials().useStep(user, SECRET, 1)).isFalse();
    }

    @Test
    void 刻みは最後に使った刻みより後のときだけ使える() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, new EncryptedSecret("ciphertext"), at(Duration.ZERO)));

        assertThat(totpCredentials().useStep(user, SECRET, 100)).isTrue();
        assertThat(totpCredentials().useStep(user, SECRET, 100)).as("同じ刻みの再利用").isFalse();
        assertThat(totpCredentials().useStep(user, SECRET, 99)).as("前の刻み").isFalse();
        assertThat(totpCredentials().useStep(user, SECRET, 101)).isTrue();
        assertThat(totpCredentials().findByUserId(user).orElseThrow().lastUsedStep())
                .isEqualTo(101);
    }

    @Test
    void 負の刻みは失敗へ倒す() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, new EncryptedSecret("ciphertext"), at(Duration.ZERO)));

        TotpCredentialRepository repository = totpCredentials();

        assertThatThrownBy(() -> repository.useStep(user, SECRET, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 照合で読んだ秘密と違う秘密の資格では刻みを使えない() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, SECRET, at(Duration.ZERO)));

        assertThat(totpCredentials().useStep(user, new EncryptedSecret("other"), 100))
                .isFalse();
        assertThat(totpCredentials().findByUserId(user).orElseThrow().lastUsedStep())
                .isZero();
    }

    @Test
    void 資格を置き換えると古い秘密では刻みを使えず最後に使った刻みは戻らない() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, SECRET, at(Duration.ZERO)));
        assertThat(totpCredentials().useStep(user, SECRET, 200)).isTrue();
        TotpCredential read = totpCredentials().findByUserId(user).orElseThrow();
        EncryptedSecret renewed = new EncryptedSecret("renewed");

        // 新しいコードの刻み（150）は最後に使った刻み（200）より前でも、最後に使った刻みは大きい方のまま
        assertThat(totpCredentials().replace(read.reEnroll(renewed, 150, at(Duration.ofDays(1))), SECRET))
                .isTrue();

        TotpCredential replaced = totpCredentials().findByUserId(user).orElseThrow();
        assertThat(replaced.secret()).isEqualTo(renewed);
        assertThat(replaced.lastUsedStep()).isEqualTo(200);
        assertThat(replaced.enrolledAt()).isEqualTo(at(Duration.ofDays(1)));
        assertThat(totpCredentials().useStep(user, SECRET, 201)).as("古い秘密").isFalse();
        assertThat(totpCredentials().useStep(user, renewed, 201)).as("新しい秘密").isTrue();
    }

    @Test
    void 読んだ後に古い秘密の要求が刻みを大きくしても置き換えは最後に使った刻みを戻さない() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, SECRET, at(Duration.ZERO)));
        TotpCredential read = totpCredentials().findByUserId(user).orElseThrow();
        // 読んだ後に、古い秘密の要求が刻み 300 を使った
        assertThat(totpCredentials().useStep(user, SECRET, 300)).isTrue();

        assertThat(totpCredentials()
                        .replace(read.reEnroll(new EncryptedSecret("renewed"), 10, at(Duration.ofDays(1))), SECRET))
                .isTrue();

        assertThat(totpCredentials().findByUserId(user).orElseThrow().lastUsedStep())
                .isEqualTo(300);
    }

    @Test
    void 読んだ秘密がもう置き換えられていたら置き換えない() {
        UserId user = newUser();
        totpCredentials().add(TotpCredential.enroll(user, SECRET, at(Duration.ZERO)));
        TotpCredential read = totpCredentials().findByUserId(user).orElseThrow();
        EncryptedSecret first = new EncryptedSecret("first");
        assertThat(totpCredentials().replace(read.reEnroll(first, 10, at(Duration.ofDays(1))), SECRET))
                .isTrue();

        // 同じ古い秘密を読んだ 2 つ目の再登録は通らない（先の再登録の回復コードを無効にしない）
        assertThat(totpCredentials()
                        .replace(read.reEnroll(new EncryptedSecret("second"), 10, at(Duration.ofDays(1))), SECRET))
                .isFalse();
        assertThat(totpCredentials().findByUserId(user).orElseThrow().secret()).isEqualTo(first);
    }

    @Test
    void 資格のない利用者の置き換えは書かない() {
        UserId user = newUser();

        assertThat(totpCredentials().replace(TotpCredential.enroll(user, SECRET, at(Duration.ZERO)), SECRET))
                .isFalse();
        assertThat(totpCredentials().findByUserId(user)).isEmpty();
    }

    @Test
    void 発行した回復コードは未使用のものだけを読み出せ一度だけ使える() {
        UserId user = newUser();
        List<RecoveryCode> issued = issue(user, "c0de");
        recoveryCodes().replaceAll(user, issued);
        RecoveryCode first = issued.getFirst();

        assertThat(recoveryCodes().findUnusedByUserId(user)).hasSize(RecoveryCode.CODES_PER_USER);
        assertThat(recoveryCodes().use(user, first.id(), at(Duration.ofMinutes(1))))
                .isTrue();
        assertThat(recoveryCodes().use(user, first.id(), at(Duration.ofMinutes(2))))
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
        recoveryCodes().replaceAll(user, issue(user, "c0de"));

        assertThat(recoveryCodes().findUnusedByUserId(user))
                .anyMatch(code -> code.matches(normalized(code("c0de", 3)), HASHER))
                .noneMatch(code -> code.matches(normalized(code("c0de", 99)), HASHER));
    }

    @Test
    void 回復コードを発行し直すと前のコードは使えなくなる() {
        UserId user = newUser();
        List<RecoveryCode> old = issue(user, "01d0");
        recoveryCodes().replaceAll(user, old);

        recoveryCodes().replaceAll(user, issue(user, "0e30"));

        assertThat(recoveryCodes().findUnusedByUserId(user))
                .hasSize(RecoveryCode.CODES_PER_USER)
                .noneMatch(code -> code.matches(normalized(code("01d0", 1)), HASHER));
        assertThat(recoveryCodes().use(user, old.getFirst().id(), at(Duration.ofMinutes(1))))
                .isFalse();
    }

    @Test
    void 回復コードは10個でなければ発行できない() {
        UserId user = newUser();

        RecoveryCodeRepository repository = recoveryCodes();
        List<RecoveryCode> nine = issue(user, "c0de").subList(0, 9);

        assertThatThrownBy(() -> repository.replaceAll(user, nine)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 存在しない回復コードは使えない() {
        assertThat(recoveryCodes().use(newUser(), UUID.randomUUID(), at(Duration.ZERO)))
                .isFalse();
    }

    @Test
    void ほかの利用者の回復コードは使えない() {
        UserId owner = newUser();
        List<RecoveryCode> issued = issue(owner, "c0de");
        recoveryCodes().replaceAll(owner, issued);

        assertThat(recoveryCodes().use(newUser(), issued.getFirst().id(), at(Duration.ZERO)))
                .isFalse();
        assertThat(recoveryCodes().findUnusedByUserId(owner)).hasSize(RecoveryCode.CODES_PER_USER);
    }

    @Test
    void 存在しない利用者の失敗では記録を作らない() {
        UserId unknown = unknownUser();

        loginFailures().prepare(unknown, at(Duration.ZERO));
        FailureCount count = loginFailures().recordFailure(unknown, at(Duration.ZERO), POLICY);

        assertThat(count.counted()).isFalse();
        assertThat(count.after().consecutiveFailures()).isZero();
        assertThat(loginFailures().findByUserId(unknown).consecutiveFailures()).isZero();
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
            failure = failAt(user, at(Duration.ofSeconds(i)));
        }
        assertThat(failure.consecutiveFailures()).isEqualTo(4);
        assertThat(failure.lockedUntil()).isEmpty();

        LoginFailure fifth = failAt(user, at(Duration.ofSeconds(4)));
        LoginFailure whileLocked = failAt(user, at(Duration.ofMinutes(10)));

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
            failAt(user, at(Duration.ofSeconds(i)));
        }

        LoginFailure afterLock = failAt(user, at(Duration.ofMinutes(20)));

        assertThat(afterLock.consecutiveFailures()).isEqualTo(1);
        assertThat(afterLock.lockedUntil()).isEmpty();
    }

    @Test
    void ロックの期限ちょうどの失敗は1回目から数え直し期限の1秒前の失敗は数えない() {
        UserId user = newUser();
        for (int i = 0; i < 5; i++) {
            failAt(user, at(Duration.ofSeconds(i)));
        }
        Duration fifth = Duration.ofSeconds(4);

        LoginFailure justBefore = failAt(user, at(fifth.plusMinutes(15).minusSeconds(1)));
        LoginFailure atExpiry = failAt(user, at(fifth.plusMinutes(15)));

        assertThat(justBefore.consecutiveFailures()).isEqualTo(5);
        assertThat(justBefore.lockedUntil()).contains(at(fifth.plusMinutes(15)));
        assertThat(atExpiry.consecutiveFailures()).isEqualTo(1);
        assertThat(atExpiry.lockedUntil()).isEmpty();
    }

    @Test
    void ロックが解けた後も5回続けばもう一度ロックする() {
        UserId user = newUser();
        for (int i = 0; i < 5; i++) {
            failAt(user, at(Duration.ofSeconds(i)));
        }
        LoginFailure again = null;
        for (int i = 0; i < 5; i++) {
            again = failAt(user, at(Duration.ofMinutes(20).plusSeconds(i)));
        }

        assertThat(again.consecutiveFailures()).isEqualTo(5);
        assertThat(again.lockedUntil()).contains(at(Duration.ofMinutes(35).plusSeconds(4)));
    }

    @Test
    void ロックが解けた後の成功は回数を0に戻しロックを外す() {
        UserId user = newUser();
        for (int i = 0; i < 5; i++) {
            failAt(user, at(Duration.ofSeconds(i)));
        }

        loginFailures().recordSuccess(user, at(Duration.ofMinutes(20)));

        LoginFailure failure = loginFailures().findByUserId(user);
        assertThat(failure.consecutiveFailures()).isZero();
        assertThat(failure.lockedUntil()).isEmpty();
        assertThat(failure.isLocked(at(Duration.ofMinutes(20)))).isFalse();
    }

    @Test
    void ロック中の成功はロックを消さない() {
        UserId user = newUser();
        for (int i = 0; i < 5; i++) {
            failAt(user, at(Duration.ofSeconds(i)));
        }

        loginFailures().recordSuccess(user, at(Duration.ofMinutes(1)));

        LoginFailure failure = loginFailures().findByUserId(user);
        assertThat(failure.consecutiveFailures()).isEqualTo(5);
        assertThat(failure.isLocked(at(Duration.ofMinutes(1)))).isTrue();
    }

    /** 記録を作ってから失敗を数え、数えた後のログインの失敗を返す（サービスと同じ順）。 */
    private LoginFailure failAt(UserId user, UtcInstant at) {
        loginFailures().prepare(user, at);
        return loginFailures().recordFailure(user, at, POLICY).after();
    }

    @Test
    void 記録の用意は何度呼んでも1つで数えた回数を変えない() {
        UserId user = newUser();
        failAt(user, at(Duration.ZERO));

        loginFailures().prepare(user, at(Duration.ofSeconds(1)));
        loginFailures().prepare(user, at(Duration.ofSeconds(2)));

        assertThat(loginFailures().findByUserId(user).consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void 数えたかを返しロック中とロックした失敗を見分けられる() {
        UserId user = newUser();
        for (int i = 0; i < 4; i++) {
            failAt(user, at(Duration.ofSeconds(i)));
        }

        FailureCount fifth = loginFailures().recordFailure(user, at(Duration.ofSeconds(4)), POLICY);
        FailureCount whileLocked = loginFailures().recordFailure(user, at(Duration.ofMinutes(10)), POLICY);

        assertThat(fifth.counted()).isTrue();
        assertThat(fifth.lockedBy(at(Duration.ofSeconds(4)))).as("この失敗でロックした").isTrue();
        assertThat(whileLocked.counted()).as("ロック中は数えない").isFalse();
        assertThat(whileLocked.lockedBy(at(Duration.ofMinutes(10)))).isFalse();
    }
}
