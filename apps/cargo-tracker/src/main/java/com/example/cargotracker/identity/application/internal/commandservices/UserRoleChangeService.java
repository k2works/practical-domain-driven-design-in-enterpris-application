package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
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
 * 確かめてから、1 つのトランザクションで、版を条件にした更新と役割の差分（{@link UserRepository#changeRoles}）と監査記録
 * {@code ROLES_CHANGED} を書く。更新が 0 件ならほかの更新が先に入ったとしてロールバックする。監査を書けなければ例外で全体を戻す
 * （IA-INV-08 の同期の書き方）。拒否と古い版は監査に書かない（Bolt 34 の確認ポイント 7）。変えた役割は、request ごとの確かめ
 * （Bolt 32c）で相手の次の request から効く。
 */
@Service
public class UserRoleChangeService {

    private final UserRepository users;
    private final CompanyRepository companies;
    private final AuditRecordRepository auditRecords;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public UserRoleChangeService(
            UserRepository users,
            CompanyRepository companies,
            AuditRecordRepository auditRecords,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.users = users;
        this.companies = companies;
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
        return refusal(operator, target, expectedVersion, newRoles).orElse(new RoleChangeResult.Acceptable());
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
        Optional<RoleChangeResult> refusal = refusal(operator, target, expectedVersion, newRoles);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        UtcInstant now = new UtcInstant(clock.instant());
        User before = users.findById(target).orElseThrow();
        User changed = before.changeRoles(newRoles);
        if (!users.changeRoles(changed, expectedVersion, operator.userId(), now)) {
            return new RoleChangeResult.Stale();
        }
        long newVersion = expectedVersion + 1;
        auditRecords.append(AuditRecord.rolesChanged(
                operator.userId(), operator.companyId(), target, newVersion, before.roles(), newRoles, now));
        return new RoleChangeResult.Changed(newVersion);
    }

    /** 受け付けない結果を返す。受け付けるなら空。 */
    private Optional<RoleChangeResult> refusal(
            AuthenticatedActor operator, UserId target, long expectedVersion, Set<Role> newRoles) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return Optional.of(new RoleChangeResult.NotPermitted());
        }
        Optional<User> user = users.findById(target);
        Optional<Company> company = user.flatMap(found -> companies.findById(found.companyId()));
        if (user.isEmpty() || company.isEmpty()) {
            return Optional.of(new RoleChangeResult.NotFound());
        }
        Optional<RoleChangeRejection> rejection =
                user.get().roleChangeRejection(operator.userId(), company.get(), newRoles);
        if (rejection.isPresent()) {
            return Optional.of(new RoleChangeResult.Rejected(rejection.get()));
        }
        if (user.get().version() != expectedVersion) {
            return Optional.of(new RoleChangeResult.Stale());
        }
        return Optional.empty();
    }
}
