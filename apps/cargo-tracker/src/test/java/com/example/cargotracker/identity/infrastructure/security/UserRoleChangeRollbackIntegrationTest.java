package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.example.cargotracker.identity.application.internal.commandservices.UserRoleChangeService;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 役割の変更で監査を書けなければ、役割も版も元のままであることを確かめる（Bolt 34。IA-INV-08 の同期の書き方。監査の書き込みを spy
 * で失敗させる。単体テストの偽物は戻らないので、PostgreSQL で確かめる）。
 */
class UserRoleChangeRollbackIntegrationTest extends IdentitySpyIntegrationTestBase {

    @Autowired
    UserRoleChangeService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 監査を書けなければ役割も版も元のまま() {
        Company operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        UserId sales = new UserId(UUID.randomUUID());
        users.add(User.of(
                sales,
                operator.id(),
                EmailAddress.of("rollback-" + sales.value() + "@example.com"),
                "営業 一郎",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SALES)));
        AuthenticatedActor admin = new AuthenticatedActor(
                new UserId(UUID.randomUUID()), operator.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
        doThrow(new IllegalStateException("監査の表に届かない"))
                .when(auditRecords)
                .append(argThat(written -> written.action() == AuditAction.ROLES_CHANGED));
        Set<Role> roles = Set.of(Role.ROUTE_DESIGNER);

        assertThatThrownBy(() -> service.change(admin, sales, 0L, roles)).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForList(
                        "SELECT role FROM identity.user_role WHERE user_id = ?", String.class, sales.value()))
                .containsExactly("SALES");
        assertThat(jdbc.queryForObject("SELECT version FROM identity.app_user WHERE id = ?", Long.class, sales.value()))
                .isZero();
    }
}
