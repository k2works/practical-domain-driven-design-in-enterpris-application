package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 役割の変更のサービス。システム管理者が利用者の役割を変える（Bolt 34。US-16 AC1 の役割の分・AC3、UC-16 の主シナリオ 4 と 4b）。
 *
 * <p>判定（{@link User#roleChangeRejection}。自分の変更・役割なし・変更なし・企業の種類・職務分離）と、読んだ版が画面の版と同じことを
 * 確かめてから（判定に使った利用者をそのまま変える）、1 つのトランザクションで、版を条件にした更新と役割の差分（{@link UserRepository#changeRoles}）と監査記録
 * {@code ROLES_CHANGED} を書く。更新が 0 件ならほかの更新が先に入ったとしてロールバックする。監査を書けなければ例外で全体を戻す
 * （IA-INV-08 の同期の書き方）。拒否と古い版は監査に書かない（Bolt 34 の確認ポイント 7）。変えた役割は、request ごとの確かめ
 * （Bolt 32c）で相手の次の request から効く。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class UserRoleChangeService {

    private final UserRepository users;
    private final CompanyRepository companies;
    private final UserAssignmentRepository assignments;
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
        this.companies = companies;
        this.assignments = assignments;
        this.auditRecords = auditRecords;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 変更を受け付けられるかを確かめる（確認の領域を開く時。何も書かない）。受け付けられれば {@link RoleChangeResult.Acceptable}。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedVersion 画面が持つ版
     * @param newRoles 新しい役割
     */
    public RoleChangeResult check(
            AuthenticatedActor operator, UserId target, long expectedVersion, Set<Role> newRoles) {
        if (judge(operator, target, expectedVersion, newRoles) instanceof Judgement.Refused(RoleChangeResult refusal)) {
            return refusal;
        }
        return new RoleChangeResult.Acceptable();
    }

    /**
     * 役割を変える。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     * @param expectedVersion 画面が持つ版
     * @param newRoles 新しい役割
     */
    public RoleChangeResult change(
            AuthenticatedActor operator, UserId target, long expectedVersion, Set<Role> newRoles) {
        return transactionTemplate.execute(status -> {
            RoleChangeResult result = changeInTransaction(operator, target, expectedVersion, newRoles);
            if (!(result instanceof RoleChangeResult.Changed)) {
                status.setRollbackOnly();
            }
            return result;
        });
    }

    private RoleChangeResult changeInTransaction(
            AuthenticatedActor operator, UserId target, long expectedVersion, Set<Role> newRoles) {
        return switch (judge(operator, target, expectedVersion, newRoles)) {
            case Judgement.Refused(RoleChangeResult refusal) -> refusal;
            case Judgement.Accepted(User before) -> {
                // 判定に使った利用者（読んだ版が画面の版と同じ）をそのまま変える。版の出どころを 1 つにする
                UtcInstant now = new UtcInstant(clock.instant());
                if (!users.changeRoles(before.changeRoles(newRoles), before.version(), operator.userId(), now)) {
                    yield new RoleChangeResult.Stale();
                }
                long newVersion = before.version() + 1;
                auditRecords.append(AuditRecord.rolesChanged(
                        operator.userId(), operator.companyId(), target, newVersion, before.roles(), newRoles, now));
                yield new RoleChangeResult.Changed(newVersion);
            }
        };
    }

    /** 判定する。受け付けるなら判定に使った利用者を、受け付けないなら結果を返す。 */
    private Judgement judge(AuthenticatedActor operator, UserId target, long expectedVersion, Set<Role> newRoles) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return new Judgement.Refused(new RoleChangeResult.NotPermitted());
        }
        Optional<User> user = users.findById(target);
        Optional<Company> company = user.flatMap(found -> companies.findById(found.companyId()));
        if (user.isEmpty() || company.isEmpty()) {
            return new Judgement.Refused(new RoleChangeResult.NotFound());
        }
        // 管理範囲（Bolt 34b。IA-INV-14）は規則より先に見る（範囲外の利用者の様子を規則の拒否で漏らさない）
        if (!new AdministrationScope(assignments.managedCompanies(operator.userId()))
                .covers(user.get().companyId())) {
            return new Judgement.Refused(new RoleChangeResult.OutOfScope());
        }
        Optional<RoleChangeRejection> rejection =
                user.get().roleChangeRejection(operator.userId(), company.get(), newRoles);
        if (rejection.isPresent()) {
            return new Judgement.Refused(new RoleChangeResult.Rejected(rejection.get()));
        }
        if (user.get().version() != expectedVersion) {
            return new Judgement.Refused(new RoleChangeResult.Stale());
        }
        return new Judgement.Accepted(user.get());
    }

    /** 判定の結果（このクラスの中だけで使う）。 */
    private sealed interface Judgement {

        /** 受け付ける。判定に使った利用者を持つ。 */
        record Accepted(User user) implements Judgement {}

        /** 受け付けない。 */
        record Refused(RoleChangeResult result) implements Judgement {}
    }
}
