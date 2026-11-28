package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationService;
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
 * 利用者の登録で監査を書けなければ、利用者も役割も残らないことを確かめる（Bolt 38b2。行 10、IA-INV-08 の同期の書き方）。
 */
class UserRegistrationRollbackIntegrationTest extends IdentitySpyIntegrationTestBase {

    @Autowired
    UserRegistrationService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 監査を書けなければ利用者も役割も残らない() {
        Company operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        UserId adminId = new UserId(UUID.randomUUID());
        users.add(User.of(
                adminId,
                operator.id(),
                EmailAddress.of("admin-" + adminId.value() + "@example.com"),
                "管理 次郎",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SYSTEM_ADMIN)));
        AdministrationFixtures.assignCompany(jdbc, adminId, operator.id());
        AuthenticatedActor admin =
                new AuthenticatedActor(adminId, operator.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
        doThrow(new IllegalStateException("監査の表に届かない"))
                .when(auditRecords)
                .append(argThat(written -> written.action() == AuditAction.USER_REGISTERED));
        EmailAddress email = EmailAddress.of("rollback-" + UUID.randomUUID() + "@example.com");
        Set<Role> roles = Set.of(Role.SALES);
        CompanyId company = operator.id();

        assertThatThrownBy(() -> service.register(admin, company, email, "新人 花子", roles))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("監査の表に届かない");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.app_user WHERE email = ?", Integer.class, email.value()))
                .isZero();
    }
}
