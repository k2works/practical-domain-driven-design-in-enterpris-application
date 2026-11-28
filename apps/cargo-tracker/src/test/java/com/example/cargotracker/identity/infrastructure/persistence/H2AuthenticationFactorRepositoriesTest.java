package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.domain.model.aggregates.AuthenticationFactorRepositoriesContract;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.shared.domain.UserId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOTP の資格・回復コード・ログインの失敗のリポジトリの契約を、ローカル起動と同じ dev プロファイルの H2 で確かめる
 * （Bolt 30。マッパーの SQL が H2 と PostgreSQL の共通の構文で動くこと。ADR-007）。並行は PostgreSQL で確かめる。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class H2AuthenticationFactorRepositoriesTest extends AuthenticationFactorRepositoriesContract {

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
    protected UserId unknownUser() {
        return new UserId(UUID.randomUUID());
    }

    @Override
    protected UserId newUser() {
        return AuthenticationFactorFixtures.newUser(companies, users);
    }
}
