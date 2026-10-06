package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.DuplicateEmailException;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 企業・利用者・役割・監査記録の表とリポジトリを PostgreSQL 18 で確かめる（Bolt 14、データモデル「アクセス・監査」）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisIdentityRepositoriesIntegrationTest {

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    AuditRecordRepository auditRecords;

    @Autowired
    JdbcTemplate jdbc;

    private Company company;

    @BeforeEach
    void 企業を登録する() {
        company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(company);
    }

    private User user(String email, UserStatus status, Set<Role> roles) {
        return User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                EmailAddress.of(email),
                "荷主 太郎",
                "{bcrypt}$2a$10$8VBArBH3isskZmY/uml47Ov2JmySt9Fg0phh4efSlzNLiBnT2KDqO",
                status,
                roles);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void 登録した企業を読み出せる() {
        assertThat(companies.findById(company.id())).hasValueSatisfying(found -> {
            assertThat(found.name()).isEqualTo("荷主 A");
            assertThat(found.kind()).isEqualTo(CompanyKind.SHIPPER);
            assertThat(found.active()).isTrue();
        });
    }

    @Test
    void 登録した利用者と役割をメールアドレスで読み出せる() {
        String email = uniqueEmail();
        User registered = user(email, UserStatus.ACTIVE, Set.of(Role.SHIPPER, Role.CUSTOMER_SUPPORT));
        users.add(registered);

        assertThat(users.findByEmail(EmailAddress.of(email.toUpperCase(java.util.Locale.ROOT))))
                .hasValueSatisfying(found -> {
                    assertThat(found.id()).isEqualTo(registered.id());
                    assertThat(found.companyId()).isEqualTo(company.id());
                    assertThat(found.displayName()).isEqualTo("荷主 太郎");
                    assertThat(found.passwordHash()).isEqualTo(registered.passwordHash());
                    assertThat(found.status()).isEqualTo(UserStatus.ACTIVE);
                    assertThat(found.roles()).containsExactlyInAnyOrder(Role.SHIPPER, Role.CUSTOMER_SUPPORT);
                });
        assertThat(users.findById(registered.id())).isPresent();
    }

    @Test
    void 利用停止の状態を読み出せる() {
        String email = uniqueEmail();
        users.add(user(email, UserStatus.SUSPENDED, Set.of(Role.SALES)));

        assertThat(users.findByEmail(EmailAddress.of(email)))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(UserStatus.SUSPENDED));
    }

    @Test
    void 登録していないメールアドレスは見つからない() {
        assertThat(users.findByEmail(EmailAddress.of(uniqueEmail()))).isEmpty();
    }

    @Test
    void 同じメールアドレスの利用者は2人登録できない() {
        String email = uniqueEmail();
        users.add(user(email, UserStatus.ACTIVE, Set.of(Role.SHIPPER)));

        assertThatThrownBy(() -> users.add(user(email, UserStatus.ACTIVE, Set.of(Role.SHIPPER))))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void 正規化していないメールアドレスは表が受け付けない() {
        assertThatThrownBy(() -> insertUser("Upper@Example.com", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 利用状態の値は表が確かめる() {
        assertThatThrownBy(() -> insertUser(uniqueEmail(), "LOCKED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 役割の値は表が確かめる() {
        UUID userId = insertUser(uniqueEmail(), "ACTIVE");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO identity.user_role (user_id, role, granted_at) VALUES (?, 'MANAGER', now())",
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 企業の種類の値は表が確かめる() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO identity.company"
                                + " (id, name, kind, active, version, created_at, updated_at)"
                                + " VALUES (?, '不明', 'PARTNER', TRUE, 0, now(), now())",
                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 監査記録を追記し操作者で読み出せる() {
        UserId actor = new UserId(UUID.randomUUID());
        UtcInstant first = new UtcInstant(Instant.parse("2026-10-06T05:00:00Z"));
        UtcInstant second = new UtcInstant(Instant.parse("2026-10-06T05:01:00Z"));
        auditRecords.append(
                AuditRecord.loginFailed(actor, company.id(), AuthenticationRejection.BAD_CREDENTIALS, first));
        auditRecords.append(AuditRecord.loginSucceeded(actor, company.id(), second));

        assertThat(auditRecords.findByActorUserId(actor))
                .extracting(AuditRecord::action, AuditRecord::reason, AuditRecord::occurredAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(AuditAction.LOGIN_FAILED, "BAD_CREDENTIALS", first),
                        org.assertj.core.groups.Tuple.tuple(AuditAction.LOGIN_SUCCEEDED, null, second));
    }

    @Test
    void 操作者のない監査記録も追記できる() {
        AuditRecord record = AuditRecord.loginFailed(
                null,
                null,
                AuthenticationRejection.UNKNOWN_USER,
                new UtcInstant(Instant.parse("2026-10-06T05:00:00Z")));

        auditRecords.append(record);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.audit_record WHERE id = ? AND actor_user_id IS NULL"
                                + " AND reason = 'UNKNOWN_USER'",
                        Integer.class,
                        record.id()))
                .isEqualTo(1);
    }

    @Test
    void sessionの表がplatformスキーマにある() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'platform'"
                                + " AND table_name IN ('spring_session', 'spring_session_attributes')",
                        Integer.class))
                .isEqualTo(2);
    }

    private UUID insertUser(String email, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO identity.app_user"
                        + " (id, company_id, email, display_name, password_hash, status, version, created_at, updated_at)"
                        + " VALUES (?, ?, ?, '利用者', '{noop}x', ?, 0, now(), now())",
                id,
                company.id().value(),
                email,
                status);
        return id;
    }
}
