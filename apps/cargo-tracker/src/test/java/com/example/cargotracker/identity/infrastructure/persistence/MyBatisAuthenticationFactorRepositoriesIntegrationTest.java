package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.AuthenticationFactorRepositoriesContract;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOTP の資格・回復コード・ログインの失敗のリポジトリの契約を PostgreSQL 18 で確かめる（Bolt 30、ADR-007）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisAuthenticationFactorRepositoriesIntegrationTest extends AuthenticationFactorRepositoriesContract {

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    RecoveryCodeRepository recoveryCodes;

    @Autowired
    LoginFailureRepository loginFailures;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Override
    protected TotpCredentialRepository totpCredentials() {
        return totpCredentials;
    }

    @Override
    protected RecoveryCodeRepository recoveryCodes() {
        return recoveryCodes;
    }

    @Override
    protected LoginFailureRepository loginFailures() {
        return loginFailures;
    }

    @Override
    protected UserId newUser() {
        return AuthenticationFactorFixtures.newUser(companies, users);
    }

    @Test
    void 存在しない利用者の失敗では行を作らない() {
        UserId unknown = new UserId(UUID.randomUUID());

        assertThat(loginFailures
                        .recordFailure(unknown, at(Duration.ZERO), new LockPolicy())
                        .consecutiveFailures())
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.login_failure WHERE user_id = ?",
                        Integer.class,
                        unknown.value()))
                .isZero();
    }

    @Test
    void 回復コードは平文でなくハッシュで表に書く() {
        UserId user = newUser();
        recoveryCodes.replaceAll(
                user,
                IntStream.rangeClosed(1, RecoveryCode.CODES_PER_USER)
                        .mapToObj(i -> RecoveryCode.issue(user, "plain-" + i, HASHER, at(Duration.ZERO)))
                        .toList());

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.recovery_code WHERE user_id = ? AND code_hash LIKE 'plain-%'",
                        Integer.class, user.value()))
                .isZero();
    }

    /** 外部キーのための企業と利用者を作る（並行のテストと共有する）。 */
    static final class AuthenticationFactorFixtures {

        private AuthenticationFactorFixtures() {}

        static UserId newUser(CompanyRepository companies, UserRepository users) {
            Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
            companies.add(company);
            UserId userId = new UserId(UUID.randomUUID());
            users.add(User.of(
                    userId,
                    company.id(),
                    EmailAddress.of("user-" + UUID.randomUUID() + "@example.com"),
                    "荷主 太郎",
                    "{noop}x",
                    UserStatus.ACTIVE,
                    Set.of(Role.SHIPPER)));
            return userId;
        }
    }
}
