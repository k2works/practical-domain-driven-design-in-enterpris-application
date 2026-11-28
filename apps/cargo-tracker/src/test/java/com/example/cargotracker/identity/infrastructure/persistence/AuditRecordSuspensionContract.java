package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 停止の記録の照会の契約（Bolt 38c2。US-16 AC5、#50）。対象の利用者の最新の監査記録 {@code USER_SUSPENDED} を、停止した人の今の
 * 表示名とともに 1 行引く。H2（dev）と PostgreSQL の両方で同じ SQL（並び・LIMIT・LEFT JOIN）が動くことを確かめる（ADR-007、T-214）。
 * 実装は MyBatis だけなので、この契約は persistence に置く（前例 {@code UserStandingRepositoryContract}）。
 */
abstract class AuditRecordSuspensionContract {

    private static final Instant BASE = Instant.parse("2026-11-09T01:00:00Z");

    protected abstract AuditRecordRepository auditRecords();

    protected abstract CompanyRepository companies();

    protected abstract UserRepository users();

    protected abstract JdbcTemplate jdbc();

    private Company newCompany() {
        return newCompany("A 社", CompanyKind.OPERATOR);
    }

    private Company newCompany(String name, CompanyKind kind) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), name, kind, true);
        companies().add(company);
        return company;
    }

    private User newUser(Company company, String displayName, Role role) {
        User user = User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                EmailAddress.of("user-" + UUID.randomUUID() + "@example.com"),
                displayName,
                "{noop}x",
                UserStatus.ACTIVE,
                Set.of(role));
        users().add(user);
        return user;
    }

    private static UtcInstant at(long minutes) {
        return new UtcInstant(BASE.plusSeconds(minutes * 60));
    }

    private AuditRecord suspended(User actor, User target, SuspensionReason reason, long minutes) {
        AuditRecord suspension = AuditRecord.userSuspended(
                actor.id(), actor.companyId(), target.id(), 1, UserStatus.ACTIVE, reason, at(minutes));
        auditRecords().append(suspension);
        return suspension;
    }

    private void reactivated(User actor, User target, long minutes) {
        auditRecords()
                .append(AuditRecord.userReactivated(
                        actor.id(), actor.companyId(), target.id(), 2, UserStatus.SUSPENDED, at(minutes)));
    }

    @Test
    void 停止と再開と停止の後は最新の停止の理由と停止した人の今の表示名と時刻を引く() {
        // 行 4（2 回の停止は時刻と理由を変える。V3 の並びの逆を値で見分ける）
        Company company = newCompany();
        User admin = newUser(company, "高橋", Role.SYSTEM_ADMIN);
        User sales = newUser(company, "佐藤", Role.SALES);
        suspended(admin, sales, SuspensionReason.TRANSFERRED, 1);
        auditRecords()
                .append(AuditRecord.userReactivated(
                        admin.id(), company.id(), sales.id(), 2, UserStatus.SUSPENDED, at(2)));
        suspended(admin, sales, SuspensionReason.EMERGENCY, 3);
        jdbc().update(
                        "UPDATE identity.app_user SET display_name = ? WHERE id = ?",
                        "高橋 一郎",
                        admin.id().value());

        assertThat(auditRecords().findLatestUserSuspension(sales.id()))
                .contains(new SuspensionRecord(SuspensionReason.EMERGENCY, admin.id(), Optional.of("高橋 一郎"), at(3)));
    }

    @Test
    void 停止より新しいほかの操作は選ばない() {
        // 行 5a（停止中の役割の変更は許されている）
        Company company = newCompany();
        User admin = newUser(company, "高橋", Role.SYSTEM_ADMIN);
        User sales = newUser(company, "佐藤", Role.SALES);
        suspended(admin, sales, SuspensionReason.TRANSFERRED, 1);
        auditRecords()
                .append(AuditRecord.rolesChanged(
                        admin.id(),
                        company.id(),
                        sales.id(),
                        2,
                        Set.of(Role.SALES),
                        Set.of(Role.SALES, Role.ROUTE_DESIGNER),
                        at(2)));

        assertThat(auditRecords().findLatestUserSuspension(sales.id())).hasValueSatisfying(found -> {
            assertThat(found.reason()).isEqualTo(SuspensionReason.TRANSFERRED);
            assertThat(found.occurredAt()).isEqualTo(at(1));
        });
    }

    @Test
    void ほかの利用者のより新しい停止は選ばない() {
        // 行 5b
        Company company = newCompany();
        User admin = newUser(company, "高橋", Role.SYSTEM_ADMIN);
        User sales = newUser(company, "佐藤", Role.SALES);
        User other = newUser(company, "鈴木", Role.SALES);
        suspended(admin, sales, SuspensionReason.USAGE_ENDED, 1);
        suspended(admin, other, SuspensionReason.EMERGENCY, 2);

        assertThat(auditRecords().findLatestUserSuspension(sales.id())).hasValueSatisfying(found -> {
            assertThat(found.reason()).isEqualTo(SuspensionReason.USAGE_ENDED);
            assertThat(found.occurredAt()).isEqualTo(at(1));
        });
    }

    @Test
    void 停止した人の行がなければ表示名は空で記録は引く() {
        // 行 6（監査記録の操作者に外部キーはない。INNER JOIN だと記録ごと失う。V8）
        Company company = newCompany();
        User sales = newUser(company, "佐藤", Role.SALES);
        UserId missing = new UserId(UUID.randomUUID());
        auditRecords()
                .append(AuditRecord.userSuspended(
                        missing, company.id(), sales.id(), 1, UserStatus.ACTIVE, SuspensionReason.EMERGENCY, at(1)));

        assertThat(auditRecords().findLatestUserSuspension(sales.id()))
                .contains(new SuspensionRecord(SuspensionReason.EMERGENCY, missing, Optional.empty(), at(1)));
    }

    @Test
    void 再開の後に記録のない停止なら前の停止を今の停止として引かない() {
        // 行 2a（停止 → 再開 → DB を直接書き換えた停止。今の停止の分だけを示す。決定 (3)。V9）
        Company company = newCompany();
        User admin = newUser(company, "高橋", Role.SYSTEM_ADMIN);
        User sales = newUser(company, "佐藤", Role.SALES);
        suspended(admin, sales, SuspensionReason.TRANSFERRED, 1);
        reactivated(admin, sales, 2);

        assertThat(auditRecords().findLatestUserSuspension(sales.id())).isEmpty();
    }

    @Test
    void 最新の停止の理由か操作者が欠けた行は記録なしとし前の停止に戻らない() {
        // 行 2b（DB を直接書き換えた壊れた行。詳細と確認の画面を 500 にせず「記録がありません」に落とす）
        Company company = newCompany();
        User admin = newUser(company, "高橋", Role.SYSTEM_ADMIN);
        User sales = newUser(company, "佐藤", Role.SALES);
        User other = newUser(company, "鈴木", Role.SALES);
        suspended(admin, sales, SuspensionReason.TRANSFERRED, 1);
        AuditRecord withoutReason = suspended(admin, sales, SuspensionReason.EMERGENCY, 2);
        suspended(admin, other, SuspensionReason.USAGE_ENDED, 1);
        AuditRecord withoutActor = suspended(admin, other, SuspensionReason.EMERGENCY, 2);
        jdbc().update("UPDATE identity.audit_record SET reason = NULL WHERE id = ?", withoutReason.id());
        jdbc().update("UPDATE identity.audit_record SET actor_user_id = NULL WHERE id = ?", withoutActor.id());

        assertThat(auditRecords().findLatestUserSuspension(sales.id()))
                .as("理由がない")
                .isEmpty();
        assertThat(auditRecords().findLatestUserSuspension(other.id()))
                .as("操作者がない")
                .isEmpty();
    }

    @Test
    void 停止した人が別の企業の利用者でも今の表示名を引く() {
        // 決定 (11)（管理範囲の外の停止した人も表示名を示す。照会は範囲で絞らない）
        User admin = newUser(newCompany("B 社", CompanyKind.OPERATOR), "高橋", Role.SYSTEM_ADMIN);
        User shipper = newUser(newCompany("荷主 C 社", CompanyKind.SHIPPER), "佐藤", Role.SHIPPER);
        suspended(admin, shipper, SuspensionReason.USAGE_ENDED, 1);

        assertThat(auditRecords().findLatestUserSuspension(shipper.id()))
                .contains(new SuspensionRecord(SuspensionReason.USAGE_ENDED, admin.id(), Optional.of("高橋"), at(1)));
    }

    @Test
    void 停止の記録がなければ空() {
        // 行 2 の読み出しの側（DB を直接書き換えた停止）
        User sales = newUser(newCompany(), "佐藤", Role.SALES);

        assertThat(auditRecords().findLatestUserSuspension(sales.id())).isEmpty();
    }
}
