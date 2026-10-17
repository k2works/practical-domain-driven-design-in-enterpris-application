package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 役割の変更のサービス。システム管理者が利用者の役割を変える（Bolt 34。US-16 AC1 の役割の分・AC3、UC-16 の主シナリオ 4 と 4b）。
 *
 * <p>判定（{@link User#decideRoleChange}。自分の変更・役割なし・変更なし・企業の種類・職務分離）と、読んだ版が画面の版と同じことを
 * 確かめてから（判定に使った利用者をそのまま変える）、1 つのトランザクションで、版を条件にした更新と役割の差分（{@link UserRepository#changeRoles}）と監査記録
 * {@code ROLES_CHANGED} を書く。更新が 0 件ならほかの更新が先に入ったとしてロールバックする。監査を書けなければ例外で全体を戻す
 * （IA-INV-08 の同期の書き方）。拒否と古い版は監査に書かない（Bolt 34 の確認ポイント 7）。変えた役割は、request ごとの確かめ
 * （Bolt 32c）で相手の次の request から効く。判定の前半（操作者・利用者と企業・管理範囲）は {@link ManagedUserLookup} が持つ
 * （Bolt 34e）。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class UserRoleChangeService {

    private final UserRepository users;
    private final ManagedUserLookup lookup;
    private final AuditRecordRepository auditRecords;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public UserRoleChangeService(
            UserRepository users,
            CompanyRepository companies,
            UserAssignmentRepository assignments,
            AuditRecordRepository auditRecords,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.users = users;
        this.lookup = new ManagedUserLookup(users, companies, assignments);
        this.auditRecords = auditRecords;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 変更を受け付けられるかを確かめる（確認の領域を開く時。何も書かない）。受け付けられれば {@link RoleChangeCheck.Acceptable}。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedVersion 画面が持つ版
     * @param newRoles 新しい役割
     */
    public RoleChangeCheck check(AuthenticatedActor operator, UserId target, long expectedVersion, Set<Role> newRoles) {
        return switch (judge(operator, target, expectedVersion, newRoles)) {
            case Judgement.Refused(var refusal) -> new RoleChangeCheck.Refused(refusal);
            case Judgement.Accepted _ -> new RoleChangeCheck.Acceptable();
        };
    }

    /**
     * 役割を変える。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedVersion 画面が持つ版
     * @param newRoles 新しい役割
     */
    public RoleChangeOutcome change(
            AuthenticatedActor operator, UserId target, long expectedVersion, Set<Role> newRoles) {
        return transactionTemplate.execute(status -> {
            RoleChangeOutcome result = changeInTransaction(operator, target, expectedVersion, newRoles);
            if (!(result instanceof RoleChangeOutcome.Changed)) {
                status.setRollbackOnly();
            }
            return result;
        });
    }

    private RoleChangeOutcome changeInTransaction(
            AuthenticatedActor operator, UserId target, long expectedVersion, Set<Role> newRoles) {
        return switch (judge(operator, target, expectedVersion, newRoles)) {
            case Judgement.Refused(var refusal) -> new RoleChangeOutcome.Refused(refusal);
            case Judgement.Accepted(User before, User.RoleChangeApproval approval) -> {
                // 判定に使った利用者（読んだ版が画面の版と同じ）を、その判定の承認で変える。版の出どころは承認の 1 つ（Bolt 34e）
                UtcInstant now = new UtcInstant(clock.instant());
                if (!users.changeRoles(before.changeRoles(approval), approval.version(), operator.userId(), now)) {
                    yield new RoleChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>());
                }
                // 確定の後の監査の値も承認から取る（引数の新しい役割は判定にだけ使う。2026-10-17、human:kakimomokuri）
                long newVersion = approval.version() + 1;
                auditRecords.append(AuditRecord.rolesChanged(
                        operator.userId(),
                        operator.companyId(),
                        target,
                        newVersion,
                        before.roles(),
                        approval.roles(),
                        now));
                yield new RoleChangeOutcome.Changed(newVersion);
            }
        };
    }

    /** 判定する（操作者 → 利用者と企業 → 管理範囲 → 規則 → 版）。受け付けるなら判定に使った利用者を、受け付けないなら理由を返す。 */
    private Judgement judge(AuthenticatedActor operator, UserId target, long expectedVersion, Set<Role> newRoles) {
        ManagedUserLookup.Managed managed;
        switch (lookup.find(operator, target)) {
            case ManagedUserLookup.Refusal refusal -> {
                return new Judgement.Refused(refusal.toRefusal());
            }
            case ManagedUserLookup.Managed found -> managed = found;
        }
        User user = managed.user();
        User.RoleChangeApproval approval;
        switch (user.decideRoleChange(operator.userId(), managed.company(), newRoles)) {
            case User.RoleChangeDecision.Refused(RoleChangeRejection rejection) -> {
                return new Judgement.Refused(new UserAdministrationRefusal.Rejected<>(rejection));
            }
            case User.RoleChangeDecision.Approved(User.RoleChangeApproval approved) -> approval = approved;
        }
        if (approval.version() != expectedVersion) {
            return new Judgement.Refused(new UserAdministrationRefusal.Stale<>());
        }
        return new Judgement.Accepted(user, approval);
    }

    /** 判定の結果（このクラスの中だけで使う）。 */
    private sealed interface Judgement {

        /** 受け付ける。判定に使った利用者とその承認を持つ。 */
        record Accepted(User user, User.RoleChangeApproval approval) implements Judgement {}

        /** 受け付けない。 */
        record Refused(UserAdministrationRefusal<RoleChangeRejection> refusal) implements Judgement {}
    }
}
