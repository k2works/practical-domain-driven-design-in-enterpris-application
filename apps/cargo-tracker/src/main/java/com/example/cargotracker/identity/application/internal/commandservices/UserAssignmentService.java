package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.application.internal.queryservices.AssignableCompanyQuery;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignedCompanies;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignmentChangeRejection;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 担当範囲の変更のサービス。システム管理者が業務担当者の担当企業を割り当て・解除する（Bolt 38e。US-16 AC1 の担当の関係と変更履歴、
 * IA-INV-02・IA-INV-14）。
 *
 * <p>判定の順は役割の変更（{@link UserRoleChangeService}）と同じく、操作者・利用者と企業・管理範囲（{@link ManagedUserLookup}）→ 規則
 * （担当範囲のある役割か）→ 選んだ企業が選べる企業（操作者の管理範囲の中の有効な荷主。照会と同じ口 {@link AssignableCompanyQuery}）か → 変更があるか
 * → 版。受け付けたら 1 つのトランザクションで、版を条件にした置き換え（{@link UserAssignmentRepository#replaceCompanies}。選べる企業の中の
 * 差だけ）と監査記録 {@code ASSIGNMENTS_CHANGED} を書く。置き換えが 0 件ならほかの更新が先に入ったとしてロールバックする。監査を書けなければ
 * 例外で全体を戻す（IA-INV-08 の同期の書き方）。拒否と古い版は監査に書かない。業務の画面で担当企業が効くのは Bolt 38f・38g から。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class UserAssignmentService {

    private final ManagedUserLookup lookup;
    private final AssignableCompanyQuery assignable;
    private final UserAssignmentRepository assignments;
    private final AuditRecordRepository auditRecords;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public UserAssignmentService(
            UserRepository users,
            CompanyRepository companies,
            UserAssignmentRepository assignments,
            AuditRecordRepository auditRecords,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.lookup = new ManagedUserLookup(users, companies, assignments);
        this.assignable = new AssignableCompanyQuery(companies, assignments);
        this.assignments = assignments;
        this.auditRecords = auditRecords;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 変更を受け付けられるかを確かめる（確認の領域を開く時。何も書かない）。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedVersion 画面が持つ版
     * @param selected 選んだ担当企業
     */
    public AssignmentChangeCheck check(
            AuthenticatedActor operator, UserId target, long expectedVersion, Set<CompanyId> selected) {
        return switch (judge(operator, target, expectedVersion, selected)) {
            case Judgement.Refused(var refusal) -> new AssignmentChangeCheck.Refused(refusal);
            case Judgement.Accepted _ -> new AssignmentChangeCheck.Acceptable();
        };
    }

    /**
     * 担当企業を変える。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedVersion 画面が持つ版
     * @param selected 選んだ担当企業
     */
    public AssignmentChangeOutcome change(
            AuthenticatedActor operator, UserId target, long expectedVersion, Set<CompanyId> selected) {
        return transactionTemplate.execute(status -> {
            AssignmentChangeOutcome result = changeInTransaction(operator, target, expectedVersion, selected);
            if (!(result instanceof AssignmentChangeOutcome.Changed)) {
                status.setRollbackOnly();
            }
            return result;
        });
    }

    private AssignmentChangeOutcome changeInTransaction(
            AuthenticatedActor operator, UserId target, long expectedVersion, Set<CompanyId> selected) {
        return switch (judge(operator, target, expectedVersion, selected)) {
            case Judgement.Refused(var refusal) -> new AssignmentChangeOutcome.Refused(refusal);
            case Judgement.Accepted(
                    AssignedCompanies before,
                    AdministrationScope changeable,
                    AssignedCompanies chosen) -> {
                UtcInstant now = new UtcInstant(clock.instant());
                if (!assignments.replaceCompanies(
                        target, expectedVersion, changeable, chosen, operator.userId(), now)) {
                    yield new AssignmentChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>());
                }
                long newVersion = expectedVersion + 1;
                auditRecords.append(AuditRecord.assignmentsChanged(
                        operator.userId(),
                        operator.companyId(),
                        target,
                        newVersion,
                        before,
                        before.replaceWithin(changeable, chosen),
                        now));
                yield new AssignmentChangeOutcome.Changed(newVersion);
            }
        };
    }

    /** 判定する（操作者 → 利用者と企業 → 管理範囲 → 規則 → 選べる企業 → 変更 → 版）。 */
    private Judgement judge(AuthenticatedActor operator, UserId target, long expectedVersion, Set<CompanyId> selected) {
        return switch (lookup.find(operator, target)) {
            case ManagedUserLookup.Refusal refusal -> new Judgement.Refused(refusal.toRefusal());
            case ManagedUserLookup.Managed managed -> decide(operator, managed.user(), expectedVersion, selected);
        };
    }

    /** 管理範囲の後の段。規則・選べる企業・変更・版の順に見る。 */
    private Judgement decide(AuthenticatedActor operator, User user, long expectedVersion, Set<CompanyId> selected) {
        if (!RoleAssignmentPolicy.holdsAssignmentScopedRole(user.roles())) {
            return rejected(AssignmentChangeRejection.NOT_ASSIGNMENT_SCOPED);
        }
        AdministrationScope changeable = AssignableCompanyQuery.scopeOf(assignable.of(operator.userId()));
        if (!changeable.companies().containsAll(selected)) {
            return rejected(AssignmentChangeRejection.UNSELECTABLE_COMPANY);
        }
        // 変更前の担当企業は版の条件付きの更新（行のロック）より前に読む。アプリの書き手はすべて版を上げるので一致するが、運用の手順が
        // 版を上げずに行を直接書いた場合は、監査の before が実際の行とずれうる（受け入れる。開発レビューの低-4）
        AssignedCompanies before = assignments.assignedCompaniesOf(user.id());
        AssignedCompanies chosen = new AssignedCompanies(selected);
        if (before.within(changeable).equals(chosen)) {
            return rejected(AssignmentChangeRejection.NO_CHANGE);
        }
        return user.version() == expectedVersion
                ? new Judgement.Accepted(before, changeable, chosen)
                : new Judgement.Refused(new UserAdministrationRefusal.Stale<>());
    }

    private static Judgement rejected(AssignmentChangeRejection rejection) {
        return new Judgement.Refused(new UserAdministrationRefusal.Rejected<>(rejection));
    }

    /** 判定の結果（このクラスの中だけで使う）。 */
    private sealed interface Judgement {

        /** 受け付ける。変更前の担当企業、置き換えてよい範囲、選んだ担当企業を持つ。 */
        record Accepted(AssignedCompanies before, AdministrationScope changeable, AssignedCompanies chosen)
                implements Judgement {}

        /** 受け付けない。 */
        record Refused(UserAdministrationRefusal<AssignmentChangeRejection> refusal) implements Judgement {}
    }
}
