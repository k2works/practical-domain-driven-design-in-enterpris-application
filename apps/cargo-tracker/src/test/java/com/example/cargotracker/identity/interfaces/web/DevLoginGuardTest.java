package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 開発環境の入力済みの守りの 2 層目（ADR-011 の決定 3、ADR-012）。dev の外、または staging・prod と一緒に設定があれば起動させない。
 */
class DevLoginGuardTest {

    private static final DevLoginProperties CONFIGURED = new DevLoginProperties("dev@example.com", "dev-password");
    private static final DevLoginProperties EMPTY = new DevLoginProperties(null, null);

    private static MockEnvironment profiles(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void devでは設定があっても起動できる() {
        assertThatCode(() -> new DevLoginGuard(CONFIGURED, profiles("dev"))).doesNotThrowAnyException();
    }

    @Test
    void devの外で設定があれば起動させない() {
        assertThatThrownBy(() -> new DevLoginGuard(CONFIGURED, profiles())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devとprodを一緒に使うときに設定があれば起動させない() {
        assertThatThrownBy(() -> new DevLoginGuard(CONFIGURED, profiles("dev", "prod")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devとstagingを一緒に使うときに設定があれば起動させない() {
        assertThatThrownBy(() -> new DevLoginGuard(CONFIGURED, profiles("staging", "dev")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 設定がなければどの環境でも起動できる() {
        assertThatCode(() -> new DevLoginGuard(EMPTY, profiles("prod"))).doesNotThrowAnyException();
    }
}
