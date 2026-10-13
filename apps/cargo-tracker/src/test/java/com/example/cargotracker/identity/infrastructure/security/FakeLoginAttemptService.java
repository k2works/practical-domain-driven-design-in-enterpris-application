package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.acceptance.InMemoryLoginFailureRepository;
import com.example.cargotracker.identity.application.internal.commandservices.LoginAttemptService;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * 入口の単体テストのための、ロックの有無を決めて呼び出しを覚える偽のログインの試行のサービス（Bolt 32）。親の部品はメモリの
 * 実装で埋める（null を渡さない。上書きし忘れた操作もメモリの上で動く。Bolt 32 の開発レビュー）。
 */
class FakeLoginAttemptService extends LoginAttemptService {

    private final boolean locked;
    final List<String> calls = new ArrayList<>();

    FakeLoginAttemptService(boolean locked) {
        super(
                new InMemoryLoginFailureRepository(),
                new DiscardingAuditRecords(),
                new LockPolicy(),
                Clock.systemUTC(),
                new NoTransactions());
        this.locked = locked;
    }

    @Override
    public boolean isLocked(UserId userId) {
        return locked;
    }

    @Override
    public void recordFailure(UserId userId, CompanyId companyId, AuthenticationRejection reason) {
        calls.add("failure:" + reason);
    }

    @Override
    public void recordLockedAttempt(UserId userId, CompanyId companyId) {
        calls.add("locked");
    }

    @Override
    public void recordSuccess(UserId userId, CompanyId companyId) {
        calls.add("success");
    }

    /** 監査記録を捨てる（偽のサービスの呼び出しは calls で確かめる）。 */
    private static final class DiscardingAuditRecords implements AuditRecordRepository {
        @Override
        public void append(AuditRecord auditRecord) {
            // 捨てる
        }

        @Override
        public List<AuditRecord> findByActorUserId(UserId actor) {
            return List.of();
        }
    }

    /** 何もしないトランザクションマネージャー。 */
    private static final class NoTransactions extends AbstractPlatformTransactionManager {
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
            // 何もしない
        }
    }
}
