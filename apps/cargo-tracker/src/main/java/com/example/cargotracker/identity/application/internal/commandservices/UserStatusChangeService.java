package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
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
 * 利用状態の変更のサービス。システム管理者が利用者の利用を停止し、再開する（Bolt 34d。US-16 AC5、UC-16 の 5a と 4b）。
 *
 * <p>役割の変更（{@link UserRoleChangeService}）と同じ順（操作者 → 利用者と企業 → 管理範囲 → 規則 → 版）で判定し（判定に使った利用者を
 * そのまま変える）、1 つのトランザクションで、版を条件にした利用状態の更新（{@link UserRepository#changeStatus}）と監査記録
 * {@code USER_SUSPENDED}・{@code USER_REACTIVATED} を書く。更新が 0 件ならほかの更新（役割の変更を含む。版を共有する）が先に入った
 * としてロールバックする。監査を書けなければ例外で全体を戻す（IA-INV-08）。拒否と古い版は監査に書かない。止めたことは、request ごとの
 * 確かめ（Bolt 32c。IA-INV-04）で相手の次の request から効く。停止の確定では相手の session をすぐには消さない（2026-10-17、
 * human:kakimomokuri）。
 *
 * <p>開発レビューの後の決定（2026-10-17、human:kakimomokuri）: 停止では、同じトランザクションで相手の未使用の password の再設定の
 * トークンを無効にする（停止の前に発行したリンクで、再開の後に設定し直させない）。再開では、確定の後に相手の session をすべて消す
 * （相手が 1 度も操作しないうちに停止して再開したとき、前の session を使い続けさせない。再開の後の session はログインし直したものだけ）。
 * session は Spring Session が自分のトランザクションで消すので確定の後に呼び、消せなくても再開は戻さない（password の再設定の完了と
 * 同じ扱い）。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class UserStatusChangeService {

    private static final Logger LOG = LoggerFactory.getLogger(UserStatusChangeService.class);

    private final UserRepository users;
    private final ManagedUserLookup lookup;
    private final AuditRecordRepository auditRecords;
    private final PasswordResetTokenRepository tokens;
    private final UserSessionRevoker sessions;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("java:S107") // 判定と書き込みと session の失効の部品をそろえて受ける
    public UserStatusChangeService(
            UserRepository users,
            CompanyRepository companies,
            UserAssignmentRepository assignments,
            AuditRecordRepository auditRecords,
            PasswordResetTokenRepository tokens,
            UserSessionRevoker sessions,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.users = users;
        this.lookup = new ManagedUserLookup(users, companies, assignments);
        this.auditRecords = auditRecords;
        this.tokens = tokens;
        this.sessions = sessions;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 変更を受け付けられるかを確かめる（確認の領域を開く時。何も書かない）。受け付けられれば {@link StatusChangeCheck.Acceptable}。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedVersion 画面が持つ版
     * @param to 変更後の利用状態
     * @param reason 停止の理由（停止のときに要る）
     */
    public StatusChangeCheck check(
            AuthenticatedActor operator,
            UserId target,
            long expectedVersion,
            UserStatus to,
            Optional<SuspensionReason> reason) {
        return switch (judge(operator, target, expectedVersion, to, reason)) {
            case Judgement.Refused(var refusal) -> new StatusChangeCheck.Refused(refusal);
            case Judgement.Accepted _ -> new StatusChangeCheck.Acceptable();
        };
    }

    /**
     * 利用状態を変える。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedVersion 画面が持つ版
     * @param to 変更後の利用状態
     * @param reason 停止の理由（停止のときに要る）
     */
    public StatusChangeOutcome change(
            AuthenticatedActor operator,
            UserId target,
            long expectedVersion,
            UserStatus to,
            Optional<SuspensionReason> reason) {
        // 変えたときの判定に使った利用者と承認（再開の後に session を消すため）。変えなければ空のまま
        AtomicReference<Judgement.Accepted> changed = new AtomicReference<>();
        StatusChangeOutcome result = transactionTemplate.execute(status -> {
            StatusChangeOutcome inTransaction =
                    changeInTransaction(operator, target, expectedVersion, to, reason, changed);
            if (!(inTransaction instanceof StatusChangeOutcome.Changed)) {
                status.setRollbackOnly();
            }
            return inTransaction;
        });
        if (result instanceof StatusChangeOutcome.Changed
                && changed.get().approval().to() == UserStatus.ACTIVE) {
            revokeSessions(target, changed.get().user().email());
        }
        return result;
    }

    private StatusChangeOutcome changeInTransaction(
            AuthenticatedActor operator,
            UserId target,
            long expectedVersion,
            UserStatus to,
            Optional<SuspensionReason> reason,
            AtomicReference<Judgement.Accepted> changed) {
        return switch (judge(operator, target, expectedVersion, to, reason)) {
            case Judgement.Refused(var refusal) -> new StatusChangeOutcome.Refused(refusal);
            case Judgement.Accepted accepted -> {
                // 判定に使った利用者（読んだ版が画面の版と同じ）を、その判定の承認で変える。版の出どころは承認の 1 つ（Bolt 34e）。
                // 確定の後の監査と副作用の値も承認から取る（引数の変更後の状態と理由は判定にだけ使う。2026-10-17、human:kakimomokuri）
                User before = accepted.user();
                User.StatusChangeApproval approval = accepted.approval();
                UtcInstant now = new UtcInstant(clock.instant());
                if (!users.changeStatus(before.changeStatus(approval), approval.version(), now)) {
                    yield new StatusChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>());
                }
                long newVersion = approval.version() + 1;
                if (approval.to() == UserStatus.SUSPENDED) {
                    tokens.invalidateUnused(target, now);
                    auditRecords.append(AuditRecord.userSuspended(
                            operator.userId(),
                            operator.companyId(),
                            target,
                            newVersion,
                            before.status(),
                            approval.reason().orElseThrow(),
                            now));
                } else {
                    auditRecords.append(AuditRecord.userReactivated(
                            operator.userId(), operator.companyId(), target, newVersion, before.status(), now));
                }
                changed.set(accepted);
                yield new StatusChangeOutcome.Changed(newVersion);
            }
        };
    }

    private void revokeSessions(UserId target, EmailAddress email) {
        try {
            int revoked = sessions.revokeAll(email);
            LOG.info("利用の再開の後に利用者の前の session を失効させた: 利用者 {}、件数 {}", target.value(), revoked);
        } catch (RuntimeException e) {
            LOG.warn("利用の再開の後に利用者の前の session を失効させられなかった（利用は再開している）: 利用者 {}", target.value(), e);
        }
    }

    /** 判定する（操作者 → 利用者と企業 → 管理範囲 → 規則 → 版）。受け付けるなら判定に使った利用者を、受け付けないなら理由を返す。 */
    private Judgement judge(
            AuthenticatedActor operator,
            UserId target,
            long expectedVersion,
            UserStatus to,
            Optional<SuspensionReason> reason) {
        User user;
        switch (lookup.find(operator, target)) {
            case ManagedUserLookup.Refusal refusal -> {
                return new Judgement.Refused(refusal.toRefusal());
            }
            case ManagedUserLookup.Managed managed -> user = managed.user();
        }
        User.StatusChangeApproval approval;
        switch (user.decideStatusChange(operator.userId(), to, reason)) {
            case User.StatusChangeDecision.Refused(StatusChangeRejection rejection) -> {
                return new Judgement.Refused(new UserAdministrationRefusal.Rejected<>(rejection));
            }
            case User.StatusChangeDecision.Approved(User.StatusChangeApproval approved) -> approval = approved;
        }
        if (approval.version() != expectedVersion) {
            return new Judgement.Refused(new UserAdministrationRefusal.Stale<>());
        }
        return new Judgement.Accepted(user, approval);
    }

    /** 判定の結果（このクラスの中だけで使う）。 */
    private sealed interface Judgement {

        /** 受け付ける。判定に使った利用者とその承認を持つ。 */
        record Accepted(User user, User.StatusChangeApproval approval) implements Judgement {}

        /** 受け付けない。 */
        record Refused(UserAdministrationRefusal<StatusChangeRejection> refusal) implements Judgement {}
    }
}
