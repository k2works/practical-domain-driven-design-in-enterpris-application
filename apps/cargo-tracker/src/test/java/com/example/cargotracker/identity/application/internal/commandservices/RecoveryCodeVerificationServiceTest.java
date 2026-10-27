package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.acceptance.InMemoryRecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 回復コードによるログインの確かめ（Bolt 31c、US-18 AC9 のログインの分、SEC-09、IA-INV-11）。入力を正規化し、未使用のコードの
 * どれかと一致したら条件付きの更新で使い、同じトランザクションで監査記録を書く。誤り・使用済み・形の違い・回復コードのない
 * 利用者は拒み、何も書かない。
 */
class RecoveryCodeVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    private static final String PLAIN = "1a2b-3c4d";

    /** 偽のハッシュ。照合の回数を数える（形の違う入力では照合しないことを確かめる）。 */
    private static final class CountingHasher implements RecoveryCodeHasher {
        private final AtomicInteger matches = new AtomicInteger();

        @Override
        public String hash(String code) {
            return "hashed:" + code;
        }

        private final List<Boolean> inTransaction = new ArrayList<>();

        @Override
        public boolean matches(String code, String hash) {
            matches.incrementAndGet();
            inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            return hash.equals("hashed:" + code);
        }
    }

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
    private final CountingHasher hasher = new CountingHasher();
    /** 使用がトランザクションの中で呼ばれたかを覚える。 */
    private final List<Boolean> usedInTransaction = new ArrayList<>();

    private final InMemoryRecoveryCodeRepository codes = new InMemoryRecoveryCodeRepository() {
        @Override
        public synchronized boolean use(UserId userId, UUID recoveryCodeId, UtcInstant usedAt) {
            usedInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            return super.use(userId, recoveryCodeId, usedAt);
        }
    };
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
    private final RecoveryCodeVerificationService service = new RecoveryCodeVerificationService(
            codes, auditRecords, hasher, Clock.fixed(NOW, ZoneOffset.UTC), new TransactionTemplate(transactions));

    private void issueCodes(UserId owner) {
        List<RecoveryCode> issued = IntStream.range(0, RecoveryCode.CODES_PER_USER)
                .mapToObj(i -> RecoveryCode.issue(
                        owner, i == 3 ? PLAIN : String.format("0000-%04x", i), hasher, new UtcInstant(NOW)))
                .toList();
        codes.replaceAll(owner, issued);
    }

    @Test
    void 正しい回復コードで使った時刻を入れ監査記録を書いて通す() {
        issueCodes(user);

        assertThat(service.verify(user, company, PLAIN)).isEqualTo(RecoveryCodeVerification.ACCEPTED);

        assertThat(codes.findUnusedByUserId(user)).hasSize(RecoveryCode.CODES_PER_USER - 1);
        assertThat(audit).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.RECOVERY_CODE_USED);
            assertThat(entry.actorUserId()).isEqualTo(user);
            assertThat(entry.actorCompanyId()).isEqualTo(company);
            assertThat(entry.reason()).as("どのコードを使ったかは残さない").isNull();
        });
    }

    @Test
    void 照合はトランザクションの外で行い使用だけをトランザクションの中で行う() {
        // 確認ポイント 8: 最大 10 回の bcrypt の間 DB の接続を持たない
        issueCodes(user);

        service.verify(user, company, PLAIN);

        assertThat(hasher.inTransaction).isNotEmpty().containsOnly(false);
        assertThat(usedInTransaction).containsExactly(true);
    }

    @Test
    void 同時の使用で後になったら拒み監査記録を書かない() {
        issueCodes(user);
        InMemoryRecoveryCodeRepository lost = new InMemoryRecoveryCodeRepository() {
            @Override
            public synchronized boolean use(UserId userId, UUID recoveryCodeId, UtcInstant usedAt) {
                return false;
            }

            @Override
            public synchronized List<RecoveryCode> findUnusedByUserId(UserId userId) {
                return codes.findUnusedByUserId(userId);
            }
        };
        RecoveryCodeVerificationService racing = new RecoveryCodeVerificationService(
                lost, auditRecords, hasher, Clock.fixed(NOW, ZoneOffset.UTC), new TransactionTemplate(transactions));

        assertThat(racing.verify(user, company, PLAIN)).isEqualTo(RecoveryCodeVerification.REJECTED);
        assertThat(audit).isEmpty();
    }

    @Test
    void 書き写した形の違いは正規化して通す() {
        issueCodes(user);

        assertThat(service.verify(user, company, " 1A2B3C4D ")).isEqualTo(RecoveryCodeVerification.ACCEPTED);
    }

    @Test
    void 使った回復コードは二度と通らない() {
        issueCodes(user);
        service.verify(user, company, PLAIN);
        audit.clear();

        assertThat(service.verify(user, company, PLAIN)).isEqualTo(RecoveryCodeVerification.REJECTED);
        assertThat(audit).isEmpty();
    }

    @Test
    void 誤ったコードは拒み何も書かない() {
        issueCodes(user);

        assertThat(service.verify(user, company, "ffff-ffff")).isEqualTo(RecoveryCodeVerification.REJECTED);

        assertThat(codes.findUnusedByUserId(user)).hasSize(RecoveryCode.CODES_PER_USER);
        assertThat(audit).isEmpty();
    }

    @Test
    void 回復コードのない利用者は拒み何も書かない() {
        assertThat(service.verify(user, company, PLAIN)).isEqualTo(RecoveryCodeVerification.REJECTED);
        assertThat(audit).isEmpty();
    }

    @Test
    void ほかの利用者の回復コードでは通らない() {
        UserId other = new UserId(UUID.randomUUID());
        issueCodes(other);

        assertThat(service.verify(user, company, PLAIN)).isEqualTo(RecoveryCodeVerification.REJECTED);
        assertThat(codes.findUnusedByUserId(other)).hasSize(RecoveryCode.CODES_PER_USER);
    }

    @Test
    void 形の違う入力は照合せずに拒む() {
        issueCodes(user);

        assertThat(service.verify(user, company, "123456")).isEqualTo(RecoveryCodeVerification.REJECTED);
        assertThat(service.verify(user, company, null)).isEqualTo(RecoveryCodeVerification.REJECTED);
        assertThat(hasher.matches).as("bcrypt を回さない").hasValue(0);
    }

    @Test
    void 監査記録を書けなければトランザクションをロールバックさせる() {
        issueCodes(user);
        RecoveryCodeVerificationService failing = new RecoveryCodeVerificationService(
                codes,
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
                hasher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new TransactionTemplate(transactions));

        assertThatThrownBy(() -> failing.verify(user, company, PLAIN)).isInstanceOf(IllegalStateException.class);
        assertThat(transactions.rolledBack).as("使用と監査記録を 1 つのトランザクションにする").isTrue();
    }
}
