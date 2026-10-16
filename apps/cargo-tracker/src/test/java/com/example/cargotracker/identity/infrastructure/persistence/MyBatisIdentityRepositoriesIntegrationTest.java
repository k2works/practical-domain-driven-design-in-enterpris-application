package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.DuplicateEmailException;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
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
 * TOTP の資格・回復コード・ログインの失敗の表の制約も確かめる（Bolt 30）。
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
    UserAssignmentRepository assignments;

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
        User same = user(email, UserStatus.ACTIVE, Set.of(Role.SHIPPER));

        assertThatThrownBy(() -> users.add(same)).isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void 正規化していないメールアドレスは表が受け付けない() {
        assertThatThrownBy(() -> insertUser("Upper@Example.com", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 利用状態の値は表が確かめる() {
        String email = uniqueEmail();

        assertThatThrownBy(() -> insertUser(email, "LOCKED")).isInstanceOf(DataIntegrityViolationException.class);
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
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO identity.company"
                                + " (id, name, kind, active, version, created_at, updated_at)"
                                + " VALUES (?, '不明', 'PARTNER', TRUE, 0, now(), now())",
                        id))
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
        AuditRecord failure = AuditRecord.loginFailed(
                null,
                null,
                AuthenticationRejection.UNKNOWN_USER,
                new UtcInstant(Instant.parse("2026-10-06T05:00:00Z")));

        auditRecords.append(failure);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.audit_record WHERE id = ? AND actor_user_id IS NULL"
                                + " AND reason = 'UNKNOWN_USER'",
                        Integer.class,
                        failure.id()))
                .isEqualTo(1);
    }

    @Test
    void 役割の変更の監査記録は対象と変更前後を残す() {
        UserId actor = new UserId(UUID.randomUUID());
        UserId target = new UserId(UUID.randomUUID());
        UtcInstant at = new UtcInstant(Instant.parse("2026-10-16T05:00:00Z"));
        auditRecords.append(AuditRecord.rolesChanged(
                actor, company.id(), target, 3L, Set.of(Role.ROUTE_DESIGNER, Role.SALES), Set.of(Role.SALES), at));

        assertThat(auditRecords.findByActorUserId(actor)).singleElement().satisfies(audit -> {
            assertThat(audit.action()).isEqualTo(AuditAction.ROLES_CHANGED);
            assertThat(audit.target())
                    .isEqualTo(
                            new AuditTarget("USER", target.value().toString(), "3", "SALES,ROUTE_DESIGNER", "SALES"));
        });
    }

    @Test
    void 対象のない監査記録は対象の列が空() {
        UserId actor = new UserId(UUID.randomUUID());
        auditRecords.append(
                AuditRecord.logout(actor, company.id(), new UtcInstant(Instant.parse("2026-10-16T05:00:00Z"))));

        assertThat(auditRecords.findByActorUserId(actor))
                .singleElement()
                .extracting(AuditRecord::target)
                .isNull();
    }

    @Test
    void 利用者の管理の一覧は役割を1行に束ね企業名と表示名の順に並べる() {
        Company other = Company.of(new CompanyId(UUID.randomUUID()), "あ社（一覧の並び）", CompanyKind.OPERATOR, true);
        companies.add(other);
        UserId second = addUser(other, "い " + UUID.randomUUID(), Set.of(Role.SALES, Role.ROUTE_DESIGNER));
        UserId first = addUser(other, "あ " + UUID.randomUUID(), Set.of(Role.TRACKING_MANAGER));

        List<UserSummary> mine = users.findSummariesIn(Set.of(other.id()));

        assertThat(mine).extracting(UserSummary::id).containsExactly(first, second);
        assertThat(mine.get(1).roles()).containsExactlyInAnyOrder(Role.SALES, Role.ROUTE_DESIGNER);
    }

    @Test
    void 利用者の管理の詳細は企業の種類と版と役割を返す() {
        UserId id = addUser(company, "詳細 " + UUID.randomUUID(), Set.of(Role.SHIPPER));
        jdbc.update("UPDATE identity.app_user SET version = 5 WHERE id = ?", id.value());

        UserDetail detail = users.findDetail(id).orElseThrow();

        assertThat(detail.companyKind()).isEqualTo(company.kind());
        assertThat(detail.companyName()).isEqualTo(company.name());
        assertThat(detail.version()).isEqualTo(5L);
        assertThat(detail.roles()).containsExactly(Role.SHIPPER);
        assertThat(users.findById(id).orElseThrow().version()).isEqualTo(5L);
    }

    @Test
    void 役割のない利用者も一覧と詳細に出て役割は空() {
        UserId id = addUser(company, "役割なし " + UUID.randomUUID(), Set.of(Role.SHIPPER));
        jdbc.update("DELETE FROM identity.user_role WHERE user_id = ?", id.value());

        assertThat(users.findDetail(id).orElseThrow().roles()).isEmpty();
        assertThat(users.findSummariesIn(Set.of(company.id())))
                .filteredOn(summary -> summary.id().equals(id))
                .singleElement()
                .extracting(UserSummary::roles)
                .isEqualTo(Set.of());
    }

    @Test
    void 利用者の管理の一覧は管理範囲の企業の利用者だけを返し空の範囲なら空() {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        companies.add(outside);
        UserId inside = addUser(company, "範囲内 " + UUID.randomUUID(), Set.of(Role.SHIPPER));
        UserId out = addUser(outside, "範囲外 " + UUID.randomUUID(), Set.of(Role.SALES));

        assertThat(users.findSummariesIn(Set.of(company.id())))
                .extracting(UserSummary::id)
                .contains(inside)
                .doesNotContain(out);
        assertThat(users.findSummariesIn(Set.of())).isEmpty();
    }

    @Test
    void システム管理者に割り当てた企業だけを管理範囲として読む() {
        UserId admin = addUser(company, "管理 " + UUID.randomUUID(), Set.of(Role.SHIPPER));
        CompanyId other = new CompanyId(UUID.randomUUID());
        AdministrationFixtures.assignCompany(jdbc, admin, company.id());
        AdministrationFixtures.assignCompany(jdbc, admin, other);
        jdbc.update(
                "INSERT INTO identity.user_assignment (user_id, scope_kind, scope_id, granted_at)"
                        + " VALUES (?, 'BOOKING', ?, CURRENT_TIMESTAMP)",
                admin.value(),
                UUID.randomUUID());

        assertThat(assignments.scopeOf(admin).companies()).containsExactlyInAnyOrder(company.id(), other);
        assertThat(assignments.scopeOf(new UserId(UUID.randomUUID())).companies())
                .isEmpty();
    }

    @Test
    void 同じ割り当ては2行入らない() {
        UserId admin = addUser(company, "管理 " + UUID.randomUUID(), Set.of(Role.SHIPPER));
        CompanyId assigned = company.id();
        AdministrationFixtures.assignCompany(jdbc, admin, assigned);

        assertThatThrownBy(() -> AdministrationFixtures.assignCompany(jdbc, admin, assigned))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 担当範囲の種類の値は表が確かめる() {
        UUID admin = addUser(company, "管理 " + UUID.randomUUID(), Set.of(Role.SHIPPER))
                .value();
        UUID scope = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO identity.user_assignment (user_id, scope_kind, scope_id, granted_at)"
                                + " VALUES (?, 'REGION', ?, CURRENT_TIMESTAMP)",
                        admin,
                        scope))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void いない利用者の詳細は空() {
        assertThat(users.findDetail(new UserId(UUID.randomUUID()))).isEmpty();
    }

    private UserId addUser(Company owner, String displayName, Set<Role> roles) {
        UserId id = new UserId(UUID.randomUUID());
        users.add(User.of(
                id,
                owner.id(),
                EmailAddress.of("admin-read-" + id.value() + "@example.com"),
                displayName,
                "{noop}unused",
                UserStatus.ACTIVE,
                roles));
        return id;
    }

    @Test
    void sessionの表がplatformスキーマにある() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'platform'"
                                + " AND table_name IN ('spring_session', 'spring_session_attributes')",
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    void TOTPの資格を利用者ごとに1つ書いて読める() {
        UUID userId = insertUser(uniqueEmail(), "ACTIVE");
        jdbc.update(
                "INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at)"
                        + " VALUES (?, 'ciphertext', 0, now())",
                userId);

        assertThat(jdbc.queryForObject(
                        "SELECT last_used_step FROM identity.totp_credential WHERE user_id = ?", Long.class, userId))
                .isZero();
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at)"
                                + " VALUES (?, 'other', 0, now())",
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void TOTPの資格の最後に使った刻みは負にできない() {
        UUID userId = insertUser(uniqueEmail(), "ACTIVE");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at)"
                                + " VALUES (?, 'ciphertext', -1, now())",
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 回復コードは利用者ごとに複数書けて使った時刻を残せる() {
        UUID userId = insertUser(uniqueEmail(), "ACTIVE");
        UUID used = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO identity.recovery_code (id, user_id, code_hash, used_at, issued_at)"
                        + " VALUES (?, ?, 'hash-1', now(), now())",
                used,
                userId);
        jdbc.update(
                "INSERT INTO identity.recovery_code (id, user_id, code_hash, used_at, issued_at)"
                        + " VALUES (?, ?, 'hash-2', NULL, now())",
                UUID.randomUUID(),
                userId);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.recovery_code WHERE user_id = ? AND used_at IS NULL",
                        Integer.class,
                        userId))
                .isEqualTo(1);
    }

    @Test
    void ログインの失敗は利用者ごとに1行で回数は負にできない() {
        UUID userId = insertUser(uniqueEmail(), "ACTIVE");
        jdbc.update(
                "INSERT INTO identity.login_failure (user_id, consecutive_failures, locked_until, updated_at)"
                        + " VALUES (?, 0, NULL, now())",
                userId);

        assertThat(jdbc.queryForObject(
                        "SELECT consecutive_failures FROM identity.login_failure WHERE user_id = ?",
                        Integer.class,
                        userId))
                .isZero();
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE identity.login_failure SET consecutive_failures = -1 WHERE user_id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // PostgreSQL は制約の違反の後に同じトランザクションの文を受け付けないため、表ごとに分ける（ADR-007）
    @Test
    void TOTPの資格は存在しない利用者に書けない() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at)"
                                + " VALUES (?, 'ciphertext', 0, now())",
                        unknown))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 回復コードは存在しない利用者に書けない() {
        UUID id = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO identity.recovery_code (id, user_id, code_hash, used_at, issued_at)"
                                + " VALUES (?, ?, 'hash', NULL, now())",
                        id,
                        unknown))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ログインの失敗は存在しない利用者に書けない() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO identity.login_failure (user_id, consecutive_failures, locked_until, updated_at)"
                                + " VALUES (?, 1, NULL, now())",
                        unknown))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    @Test
    void passwordのハッシュを書き版を上げる() {
        User user = user(uniqueEmail(), UserStatus.ACTIVE, Set.of(Role.SHIPPER));
        users.add(user);
        long before = jdbc.queryForObject(
                "SELECT version FROM identity.app_user WHERE id = ?",
                Long.class,
                user.id().value());

        assertThat(users.changePassword(user.changePassword("{bcrypt}$2a$10$changed")))
                .isTrue();

        assertThat(users.findById(user.id()).orElseThrow().passwordHash()).isEqualTo("{bcrypt}$2a$10$changed");
        assertThat(jdbc.queryForObject(
                        "SELECT version FROM identity.app_user WHERE id = ?",
                        Long.class,
                        user.id().value()))
                .isEqualTo(before + 1);
    }

    @Test
    void いない利用者のpasswordは書かない() {
        User absent = user(uniqueEmail(), UserStatus.ACTIVE, Set.of(Role.SHIPPER));

        assertThat(users.changePassword(absent)).isFalse();
    }
}
