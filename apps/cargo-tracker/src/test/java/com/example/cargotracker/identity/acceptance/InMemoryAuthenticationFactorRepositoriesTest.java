package com.example.cargotracker.identity.acceptance;

import com.example.cargotracker.identity.domain.model.aggregates.AuthenticationFactorRepositoriesContract;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.shared.domain.UserId;
import java.util.UUID;

/**
 * メモリ上の TOTP の資格・回復コード・ログインの失敗のリポジトリが、本物と同じ契約を守ることを確かめる。
 */
class InMemoryAuthenticationFactorRepositoriesTest extends AuthenticationFactorRepositoriesContract {

    private final InMemoryTotpCredentialRepository totpCredentials = new InMemoryTotpCredentialRepository();
    private final InMemoryRecoveryCodeRepository recoveryCodes = new InMemoryRecoveryCodeRepository();
    private final InMemoryLoginFailureRepository loginFailures = new InMemoryLoginFailureRepository();

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
        UserId user = new UserId(UUID.randomUUID());
        loginFailures.registerUser(user);
        return user;
    }

    @Override
    protected UserId unknownUser() {
        return new UserId(UUID.randomUUID());
    }
}
