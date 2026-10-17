package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用状態の変更のサービス。システム管理者が利用者の利用を停止し、再開する（Bolt 34d。US-16 AC5、UC-16 の 5a と 4b）。
 *
 * <p>役割の変更（{@link UserRoleChangeService}）と同じ順（操作者 → 利用者と企業 → 管理範囲 → 規則 → 版）で判定し（判定に使った利用者を
 * そのまま変える）、1 つのトランザクションで、版を条件にした利用状態の更新（{@link UserRepository#changeStatus}）と監査記録
 * {@code USER_SUSPENDED}・{@code USER_REACTIVATED} を書く。更新が 0 件ならほかの更新（役割の変更を含む。版を共有する）が先に入った
 * としてロールバックする。監査を書けなければ例外で全体を戻す（IA-INV-08）。拒否と古い版は監査に書かない。止めたことは、request ごとの
 * 確かめ（Bolt 32c。IA-INV-04）で相手の次の request から効く。確定の後に相手の session をすぐには消さない（2026-10-17、
 * human:kakimomokuri）。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class UserStatusChangeService {

    private final UserRepository users;
    private final CompanyRepository companies;
    private final UserAssignmentRepository assignments;
    private final AuditRecordRepository auditRecords;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public UserStatusChangeService(
            UserRepository users,
            CompanyRepository companies,
            UserAssignmentRepository assignments,
            AuditRecordRepository auditRecords,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.users = users;
        this.companies = companies;
        this.assignments = assignments;
        this.auditRecords = auditRecords;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 変更を受け付けられるかを確かめる（確認の領域を開く時。何も書かない）。受け付けられれば {@link StatusChangeResult.Acceptable}。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedVersion 画面が持つ版
     * @param to 変更後の利用状態
     * @param reason 停止の理由（停止のときに要る）
     */
    public StatusChangeResult check(
            AuthenticatedActor operator,
            UserId target,
            long expectedVersion,
            UserStatus to,
            Optional<SuspensionReason> reason) {
        if (judge(operator, target, expectedVersion, to, reason)
                instanceof Judgement.Refused(StatusChangeResult refusal)) {
            return refusal;
        }
        return new StatusChangeResult.Acceptable();
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
    public StatusChangeResult change(
            AuthenticatedActor operator,
            UserId target,
            long expectedVersion,
            UserStatus to,
            Optional<SuspensionReason> reason) {
        return transactionTemplate.execute(status -> {
            StatusChangeResult result = changeInTransaction(operator, target, expectedVersion, to, reason);
            if (!(result instanceof StatusChangeResult.Changed)) {
                status.setRollbackOnly();
            }
            return result;
        });
    }

    private StatusChangeResult changeInTransaction(
            AuthenticatedActor operator,
            UserId target,
            long expectedVersion,
            UserStatus to,
            Optional<SuspensionReason> reason) {
        return switch (judge(operator, target, expectedVersion, to, reason)) {
            case Judgement.Refused(StatusChangeResult refusal) -> refusal;
            case Judgement.Accepted(User before) -> {
                // 判定に使った利用者（読んだ版が画面の版と同じ）をそのまま変える。版の出どころを 1 つにする
                UtcInstant now = new UtcInstant(clock.instant());
                if (!users.changeStatus(before.changeStatus(to), before.version(), now)) {
                    yield new StatusChangeResult.Stale();
                }
                long newVersion = before.version() + 1;
                auditRecords.append(
                        to == UserStatus.SUSPENDED
                                ? AuditRecord.userSuspended(
                                        operator.userId(),
                                        operator.companyId(),
                                        target,
                                        newVersion,
                                        reason.orElseThrow(),
                                        now)
                                : AuditRecord.userReactivated(
                                        operator.userId(), operator.companyId(), target, newVersion, now));
                yield new StatusChangeResult.Changed(newVersion);
            }
        };
    }

    /** 判定する。受け付けるなら判定に使った利用者を、受け付けないなら結果を返す。 */
    private Judgement judge(
            AuthenticatedActor operator,
            UserId target,
            long expectedVersion,
            UserStatus to,
            Optional<SuspensionReason> reason) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return new Judgement.Refused(new StatusChangeResult.NotPermitted());
        }
        Optional<User> user = users.findById(target);
        if (user.isEmpty() || companies.findById(user.get().companyId()).isEmpty()) {
            return new Judgement.Refused(new StatusChangeResult.NotFound());
        }
        // 管理範囲（IA-INV-14）は規則より先に見る（範囲外の利用者の状態を規則の拒否で漏らさない）
        if (!assignments.scopeOf(operator.userId()).covers(user.get().companyId())) {
            return new Judgement.Refused(new StatusChangeResult.OutOfScope());
        }
        Optional<StatusChangeRejection> rejection = user.get().statusChangeRejection(operator.userId(), to, reason);
        if (rejection.isPresent()) {
            return new Judgement.Refused(new StatusChangeResult.Rejected(rejection.get()));
        }
        if (user.get().version() != expectedVersion) {
            return new Judgement.Refused(new StatusChangeResult.Stale());
        }
        return new Judgement.Accepted(user.get());
    }

    /** 判定の結果（このクラスの中だけで使う）。 */
    private sealed interface Judgement {

        /** 受け付ける。判定に使った利用者を持つ。 */
        record Accepted(User user) implements Judgement {}

        /** 受け付けない。 */
        record Refused(StatusChangeResult result) implements Judgement {}
    }
}
