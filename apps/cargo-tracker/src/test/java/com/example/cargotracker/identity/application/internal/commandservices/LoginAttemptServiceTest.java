package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.acceptance.InMemoryLoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * ログインの試行のサービス（Bolt 32、US-18 AC3、IA-INV-03・IA-INV-08）。失敗を数えて 5 回目でロックし、ロック中は理由 LOCKED で
 * 記録して数えず、この失敗でロックしたときだけ ACCOUNT_LOCKED を書き、2 つの要素がそろった成功で 0 に戻す。監査記録と回数は
 * 1 つのトランザクション。
 */
class LoginAttemptServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-13T00:00:00Z");

    /** 何もしないトランザクションマネージャー。ロールバックしたかを覚える。 */
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // 何もしない
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // 何もしない
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }

    private final UserId user = new UserId(UUID.randomUUID());
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final MutableClock clock = new MutableClock();
    private final InMemoryLoginFailureRepository failures = new InMemoryLoginFailureRepository();
    private final List<AuditRecord> audit = new ArrayList<>();
    private final AuditRecordRepository auditRecords = new AuditRecordRepository() {
        @Override
        public void append(AuditRecord auditRecord) {
            audit.add(auditRecord);
        }

        @Override
        public List<AuditRecord> findByActorUserId(UserId actor) {
            return audit.stream().filter(r -> actor.equals(r.actorUserId())).toList();
        }

        @Override
        public Optional<SuspensionRecord> findLatestUserSuspension(UserId target) {
            return Optional.empty();
        }
    };
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private final LoginAttemptService service =
            new LoginAttemptService(failures, auditRecords, new LockPolicy(), clock, transactions);

    @BeforeEach
    void 利用者を登録し時計を合わせる() {
        failures.registerUser(user);
        clock.setInstant(T0);
    }

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
        }
    }

    private List<String> actions() {
        return audit.stream()
                .map(r -> r.action() + (r.reason() == null ? "" : ":" + r.reason()))
                .toList();
    }

    @Test
    void 失敗は理由とともに記録して数え4回ではロックしない() {
        fail(4);

        assertThat(failures.findByUserId(user).consecutiveFailures()).isEqualTo(4);
        assertThat(service.isLocked(user)).isFalse();
        assertThat(actions()).containsOnly("LOGIN_FAILED:BAD_CREDENTIALS").hasSize(4);
    }

    @Test
    void 五回目の失敗でロックしその失敗と同じ所でACCOUNT_LOCKEDを1回だけ書く() {
        fail(5);

        assertThat(service.isLocked(user)).isTrue();
        assertThat(actions()).endsWith("LOGIN_FAILED:BAD_CREDENTIALS", "ACCOUNT_LOCKED");
        assertThat(audit)
                .filteredOn(r -> r.action() == AuditAction.ACCOUNT_LOCKED)
                .hasSize(1);
    }

    @Test
    void ロック中の失敗は理由LOCKEDで記録し数えず期限も延ばさずロックも書き直さない() {
        fail(5);
        var lockedUntil = failures.findByUserId(user).lockedUntil();
        audit.clear();

        clock.setInstant(T0.plus(Duration.ofMinutes(10)));
        service.recordFailure(user, company, AuthenticationRejection.INVALID_TOTP);
        service.recordLockedAttempt(user, company);

        assertThat(actions()).containsExactly("LOGIN_FAILED:LOCKED", "LOGIN_FAILED:LOCKED");
        assertThat(failures.findByUserId(user).consecutiveFailures()).isEqualTo(5);
        assertThat(failures.findByUserId(user).lockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void ロックは15分で解け解けた後の失敗は1回目から数える() {
        fail(5);

        clock.setInstant(T0.plus(Duration.ofMinutes(15)).minusSeconds(1));
        assertThat(service.isLocked(user)).as("14 分 59 秒").isTrue();
        clock.setInstant(T0.plus(Duration.ofMinutes(15)));
        assertThat(service.isLocked(user)).as("15 分").isFalse();
        fail(1);
        assertThat(failures.findByUserId(user).consecutiveFailures()).isEqualTo(1);
        assertThat(service.isLocked(user)).isFalse();
    }

    @Test
    void 成功はLOGIN_SUCCEEDEDを書き回数を0に戻す() {
        fail(3);

        service.recordSuccess(user, company);

        assertThat(failures.findByUserId(user).consecutiveFailures()).isZero();
        assertThat(actions()).endsWith("LOGIN_SUCCEEDED");
    }

    @Test
    void ロック中に届いた成功はLOGIN_SUCCEEDEDを書くが回数もロックも消さない() {
        // 確かめてから成功するまでの短い間に別の session の失敗でロックされた場合（ADR-011 の決定 9 の受け入れること）
        fail(5);

        service.recordSuccess(user, company);

        assertThat(actions()).endsWith("LOGIN_SUCCEEDED");
        assertThat(failures.findByUserId(user).consecutiveFailures()).isEqualTo(5);
        assertThat(service.isLocked(user)).isTrue();
    }

    @Test
    void 記録のない利用者の失敗は記録を作らず数えない() {
        UserId unknown = new UserId(UUID.randomUUID());

        service.recordFailure(unknown, company, AuthenticationRejection.BAD_CREDENTIALS);

        assertThat(failures.findByUserId(unknown).consecutiveFailures()).isZero();
        assertThat(service.isLocked(unknown)).isFalse();
        // 入口は利用者を引いてから写すので、この場面は入口からは来ない。来ても数えなかった失敗として理由 LOCKED で残る
        assertThat(audit).singleElement().extracting(AuditRecord::reason).isEqualTo("LOCKED");
    }

    @Test
    void 監査記録を書けなければトランザクションをロールバックさせる() {
        LoginAttemptService failing = new LoginAttemptService(
                failures,
                new AuditRecordRepository() {
                    @Override
                    public void append(AuditRecord auditRecord) {
                        throw new IllegalStateException("監査記録を書けない");
                    }

                    @Override
                    public List<AuditRecord> findByActorUserId(UserId actor) {
                        return List.of();
                    }

                    @Override
                    public Optional<SuspensionRecord> findLatestUserSuspension(UserId target) {
                        return Optional.empty();
                    }
                },
                new LockPolicy(),
                clock,
                transactions);

        assertThatThrownBy(() -> failing.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS))
                .isInstanceOf(IllegalStateException.class);
        assertThat(transactions.rolledBack).as("回数と監査記録を 1 つのトランザクションにする").isTrue();
    }
}
