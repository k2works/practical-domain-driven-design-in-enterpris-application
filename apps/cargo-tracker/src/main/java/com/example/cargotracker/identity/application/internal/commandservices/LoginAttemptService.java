package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.FailureCount;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ログインの試行のサービス。password・TOTP・回復コードの失敗を数えてロックし、成功で戻し、監査記録と同じトランザクションで
 * 書く（Bolt 32、US-18 AC3、IA-INV-03・IA-INV-08、ADR-012 の見直しの 5）。数えたかはリポジトリの条件付きの更新で決まるので、
 * ロック中の失敗は理由 {@code LOCKED} で記録して数えず、「この失敗でロックした」ときだけ {@code ACCOUNT_LOCKED} を書く。
 * 認証の入口（監査のリスナー、照合の後の確認、2 つの provider）から呼ぶので、層の規則 D-5 の認証の例外の範囲（入口から
 * 呼べるのは commandservices）に置く（ロックの問い合わせ {@link #isLocked} を含む）。
 */
@Service
public class LoginAttemptService {

    private final LoginFailureRepository failures;
    private final AuditRecordRepository auditRecords;
    private final LockPolicy policy;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public LoginAttemptService(
            LoginFailureRepository failures,
            AuditRecordRepository auditRecords,
            LockPolicy policy,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.failures = failures;
        this.auditRecords = auditRecords;
        this.policy = policy;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /** その利用者が今ロック中か。 */
    public boolean isLocked(UserId userId) {
        return failures.findByUserId(userId).isLocked(now());
    }

    /**
     * 誤りの失敗を記録する。数えたら与えた理由で、ロック中で数えなかったら理由 {@code LOCKED} で監査記録を書き、この失敗で
     * ロックしたら {@code ACCOUNT_LOCKED} も書く（1 つのトランザクション）。記録の作成はトランザクションの前に済ませる
     * （一意制約の違反を受けるため。入れ子にしないので接続を 2 本持たない）。
     */
    public void recordFailure(UserId userId, CompanyId companyId, AuthenticationRejection reason) {
        UtcInstant now = now();
        failures.prepare(userId, now);
        transactionTemplate.executeWithoutResult(status -> {
            FailureCount count = failures.recordFailure(userId, now, policy);
            auditRecords.append(AuditRecord.loginFailed(
                    userId, companyId, count.counted() ? reason : AuthenticationRejection.LOCKED, now));
            if (count.lockedBy(now)) {
                auditRecords.append(AuditRecord.accountLocked(userId, companyId, now));
            }
        });
    }

    /** ロック中の試行（正しい password でロックで拒んだとき）を記録する。数えない。 */
    public void recordLockedAttempt(UserId userId, CompanyId companyId) {
        auditRecords.append(AuditRecord.loginFailed(userId, companyId, AuthenticationRejection.LOCKED, now()));
    }

    /** 2 つの要素がそろったログインの成功を記録し、回数を 0 に戻す（ロック中なら戻さない）。 */
    public void recordSuccess(UserId userId, CompanyId companyId) {
        UtcInstant now = now();
        transactionTemplate.executeWithoutResult(status -> {
            auditRecords.append(AuditRecord.loginSucceeded(userId, companyId, now));
            failures.recordSuccess(userId, now);
        });
    }

    private UtcInstant now() {
        return new UtcInstant(clock.instant());
    }
}
