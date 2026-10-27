package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.rules.IdentityVerificationPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.IdentityVerificationMethod;
import com.example.cargotracker.identity.domain.model.valueobjects.LoginCredentialRejection;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ログインの資格の管理のサービス。システム管理者がロック中の利用者のロックを外し、認証アプリを失った利用者の TOTP の登録を別経路の
 * 本人確認の記録とともに解除する（Bolt 38c。US-18 AC3 の運用の側・AC9 の管理者の経路、SEC-10）。
 *
 * <p>判定は利用状態の変更（{@link UserStatusChangeService}）と同じ順（操作者 → 利用者と企業 → 管理範囲（{@link ManagedUserLookup}）→
 * 規則 {@link User#loginCredentialChangeRejection}）の後に、今の状態（ロック中か・登録済みか・確認した登録か）を見る。どちらの操作も
 * 3 つの表（{@code login_failure}・{@code totp_credential}・{@code recovery_code}）は版を持たないので、同時の更新は条件付きの更新・削除の
 * 件数で判定し、0 件なら何も書かずにロールバックする。監査を書けなければ例外で全体を戻す（IA-INV-08）。拒否と「既に解除」は監査に
 * 書かない。
 *
 * <p>ロックの解除はロック中を条件に 0 回に戻し、監査 {@code ACCOUNT_UNLOCKED} を書く。session は消さない（IA-INV-03）。TOTP の解除は、
 * 本人確認の日時を規則（{@link IdentityVerificationPolicy}）で確かめてから、確認の画面で読んだ登録の時刻を条件に資格を消し、回復コードを
 * すべて消し、監査 {@code TOTP_RESET_BY_ADMIN} を書く。ロックは触らない。確定の後にその利用者の session をすべて消す（Spring Session
 * が自分のトランザクションで消すので確定の後に呼び、消せなくても解除は戻さない。前例は利用の再開。失効の失敗の扱いは #49）。本人への
 * 通知は Bolt 38d（2026-10-27 の開始準備の決定 (2)・(3)、human:kakimomokuri）。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class UserLoginCredentialService {

    private static final Logger LOG = LoggerFactory.getLogger(UserLoginCredentialService.class);

    private final ManagedUserLookup lookup;
    private final LoginFailureRepository loginFailures;
    private final TotpCredentialRepository totpCredentials;
    private final RecoveryCodeRepository recoveryCodes;
    private final AuditRecordRepository auditRecords;
    private final UserSessionRevoker sessions;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("java:S107") // 判定と 3 つの表の書き込みと監査と session の失効の部品をそろえて受ける
    public UserLoginCredentialService(
            UserRepository users,
            CompanyRepository companies,
            UserAssignmentRepository assignments,
            LoginFailureRepository loginFailures,
            TotpCredentialRepository totpCredentials,
            RecoveryCodeRepository recoveryCodes,
            AuditRecordRepository auditRecords,
            UserSessionRevoker sessions,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.lookup = new ManagedUserLookup(users, companies, assignments);
        this.loginFailures = loginFailures;
        this.totpCredentials = totpCredentials;
        this.recoveryCodes = recoveryCodes;
        this.auditRecords = auditRecords;
        this.sessions = sessions;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * ロックの解除を受け付けられるかを確かめる（確認の画面を開く時。何も書かない）。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     */
    public UnlockCheck checkUnlock(AuthenticatedActor operator, UserId target) {
        return judge(operator, target)
                .or(() -> loginFailures.findByUserId(target).isLocked(now())
                        ? Optional.empty()
                        : Optional.of(rejected(LoginCredentialRejection.NOT_LOCKED)))
                .<UnlockCheck>map(UnlockCheck.Refused::new)
                .orElseGet(UnlockCheck.Acceptable::new);
    }

    /**
     * ロックを解除する。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     */
    public UnlockOutcome unlock(AuthenticatedActor operator, UserId target) {
        return transactionTemplate.execute(status -> {
            UnlockOutcome outcome = unlockInTransaction(operator, target);
            if (!(outcome instanceof UnlockOutcome.Unlocked)) {
                status.setRollbackOnly();
            }
            return outcome;
        });
    }

    private UnlockOutcome unlockInTransaction(AuthenticatedActor operator, UserId target) {
        AtomicReference<User> judged = new AtomicReference<>();
        Optional<UserAdministrationRefusal<LoginCredentialRejection>> refusal = judge(operator, target, judged);
        if (refusal.isPresent()) {
            return new UnlockOutcome.Refused(refusal.get());
        }
        UtcInstant now = now();
        if (!loginFailures.unlock(target, now)) {
            return new UnlockOutcome.Refused(rejected(LoginCredentialRejection.NOT_LOCKED));
        }
        auditRecords.append(AuditRecord.accountUnlocked(
                operator.userId(), operator.companyId(), target, judged.get().version(), now));
        return new UnlockOutcome.Unlocked();
    }

    /**
     * 管理者による TOTP の解除を受け付けられるかを確かめる（確認の画面を開く時。何も書かない）。受け付けられれば読んだ登録の時刻を返す。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     */
    public TotpResetCheck checkTotpReset(AuthenticatedActor operator, UserId target) {
        Optional<UserAdministrationRefusal<LoginCredentialRejection>> refusal = judge(operator, target);
        if (refusal.isPresent()) {
            return new TotpResetCheck.Refused(refusal.get());
        }
        return totpCredentials
                .findByUserId(target)
                .<TotpResetCheck>map(credential -> new TotpResetCheck.Acceptable(credential.enrolledAt()))
                .orElseGet(() -> new TotpResetCheck.Refused(rejected(LoginCredentialRejection.NOT_ENROLLED)));
    }

    /** 本人確認の日時が規則の範囲の外なら、その理由（確認の画面が入力の誤りとまとめて示す。何も書かない）。 */
    public Optional<IdentityVerificationPolicy.Violation> verificationViolation(UtcInstant verifiedAt) {
        return IdentityVerificationPolicy.violationOf(verifiedAt, now());
    }

    /**
     * 管理者が TOTP の登録を解除する。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedEnrolledAt 確認の画面で読んだ登録の時刻
     * @param method 本人確認の手段
     * @param verifiedAt 本人確認の日時
     */
    public TotpResetOutcome resetTotp(
            AuthenticatedActor operator,
            UserId target,
            UtcInstant expectedEnrolledAt,
            IdentityVerificationMethod method,
            UtcInstant verifiedAt) {
        // 解除したときの判定に使った利用者（確定の後に session を消すため）。解除しなければ空のまま
        AtomicReference<User> judged = new AtomicReference<>();
        TotpResetOutcome result = transactionTemplate.execute(status -> {
            TotpResetOutcome outcome =
                    resetInTransaction(operator, target, expectedEnrolledAt, method, verifiedAt, judged);
            if (!(outcome instanceof TotpResetOutcome.Reset)) {
                status.setRollbackOnly();
            }
            return outcome;
        });
        if (result instanceof TotpResetOutcome.Reset) {
            revokeSessions(judged.get());
        }
        return result;
    }

    @SuppressWarnings("java:S107") // 確定の値と判定に使った利用者の受け皿をそろえて受ける
    private TotpResetOutcome resetInTransaction(
            AuthenticatedActor operator,
            UserId target,
            UtcInstant expectedEnrolledAt,
            IdentityVerificationMethod method,
            UtcInstant verifiedAt,
            AtomicReference<User> judged) {
        AtomicReference<User> user = new AtomicReference<>();
        Optional<UserAdministrationRefusal<LoginCredentialRejection>> refusal = judge(operator, target, user)
                .or(() -> enrollmentRejection(target, expectedEnrolledAt).map(UserLoginCredentialService::rejected));
        if (refusal.isPresent()) {
            return new TotpResetOutcome.Refused(refusal.get());
        }
        // 規則の拒否と登録の変化を、本人確認の日時の誤りより先に返す（確認の画面に戻しても直せないため）
        Optional<IdentityVerificationPolicy.Violation> violation = verificationViolation(verifiedAt);
        if (violation.isPresent()) {
            return new TotpResetOutcome.Unverifiable(violation.get());
        }
        if (!totpCredentials.remove(target, expectedEnrolledAt)) {
            // 読んだ後に、ほかの解除か本人の登録し直しが先に入った。読み直して理由を選ぶ
            return new TotpResetOutcome.Refused(rejected(enrollmentRejection(target, expectedEnrolledAt)
                    .orElse(LoginCredentialRejection.ENROLLMENT_CHANGED)));
        }
        recoveryCodes.removeAll(target);
        auditRecords.append(AuditRecord.totpResetByAdmin(
                operator.userId(), operator.companyId(), target, user.get().version(), method, verifiedAt, now()));
        judged.set(user.get());
        return new TotpResetOutcome.Reset();
    }

    /** 確認した登録が今も残っているか。資格がなければ未登録、登録の時刻が違えば登録が変わった。 */
    private Optional<LoginCredentialRejection> enrollmentRejection(UserId target, UtcInstant expectedEnrolledAt) {
        Optional<TotpCredential> credential = totpCredentials.findByUserId(target);
        if (credential.isEmpty()) {
            return Optional.of(LoginCredentialRejection.NOT_ENROLLED);
        }
        return credential.get().enrolledAt().equals(expectedEnrolledAt)
                ? Optional.empty()
                : Optional.of(LoginCredentialRejection.ENROLLMENT_CHANGED);
    }

    private void revokeSessions(User user) {
        try {
            int revoked = sessions.revokeAll(user.email());
            LOG.info(
                    "管理者による TOTP の解除の後に利用者の session を失効させた: 利用者 {}、件数 {}",
                    user.id().value(),
                    revoked);
        } catch (RuntimeException e) {
            LOG.warn(
                    "管理者による TOTP の解除の後に利用者の session を失効させられなかった（解除はしている）: 利用者 {}",
                    user.id().value(),
                    e);
        }
    }

    private Optional<UserAdministrationRefusal<LoginCredentialRejection>> judge(
            AuthenticatedActor operator, UserId target) {
        return judge(operator, target, new AtomicReference<>());
    }

    /** 判定する（操作者 → 利用者と企業 → 管理範囲 → 規則）。受け付けるなら判定に使った利用者を {@code judged} に置き、空を返す。 */
    private Optional<UserAdministrationRefusal<LoginCredentialRejection>> judge(
            AuthenticatedActor operator, UserId target, AtomicReference<User> judged) {
        return switch (lookup.find(operator, target)) {
            case ManagedUserLookup.Refusal refusal -> Optional.of(refusal.toRefusal());
            case ManagedUserLookup.Managed managed -> {
                judged.set(managed.user());
                yield managed.user()
                        .loginCredentialChangeRejection(operator.userId())
                        .map(UserLoginCredentialService::rejected);
            }
        };
    }

    private static UserAdministrationRefusal<LoginCredentialRejection> rejected(LoginCredentialRejection rejection) {
        return new UserAdministrationRefusal.Rejected<>(rejection);
    }

    private UtcInstant now() {
        return new UtcInstant(clock.instant());
    }
}
