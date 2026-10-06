package com.example.cargotracker.identity.devlogin;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 開発環境の入力済みと開発用の利用者の守り（ADR-011 の決定 3、ADR-012）。dev の外、または staging・prod と一緒に、
 * 入力済みの設定や開発用のデータの Flyway の場所があれば起動させない。Flyway のマイグレーションより前に確かめる（Bolt 14 レビュー）。
 */
class DevLoginGuardTest {

    private static final DevLoginProperties CONFIGURED = new DevLoginProperties(
            List.of(new DevLoginProperties.Account(Role.SHIPPER, "dev@example.com", "dev-password")));
    private static final DevLoginProperties EMPTY = new DevLoginProperties(null);
    private static final String DEV_DATA_LOCATIONS = "classpath:db/migration/common,classpath:db/dev-data";

    private static MockEnvironment profiles(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void devでは設定があっても起動できる() {
        MockEnvironment dev = profiles("dev").withProperty("spring.flyway.locations", DEV_DATA_LOCATIONS);

        assertThatCode(() -> DevLoginGuard.check(CONFIGURED, dev)).doesNotThrowAnyException();
    }

    @Test
    void devの外で設定があれば起動させない() {
        MockEnvironment none = profiles();

        assertThatThrownBy(() -> DevLoginGuard.check(CONFIGURED, none)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devとprodを一緒に使うときに設定があれば起動させない() {
        MockEnvironment devProd = profiles("dev", "prod");

        assertThatThrownBy(() -> DevLoginGuard.check(CONFIGURED, devProd)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devとstagingを一緒に使うときに設定があれば起動させない() {
        MockEnvironment stagingDev = profiles("staging", "dev");

        assertThatThrownBy(() -> DevLoginGuard.check(CONFIGURED, stagingDev)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devの外で開発用のデータのFlywayの場所があれば起動させない() {
        MockEnvironment prod = profiles("prod").withProperty("spring.flyway.locations", DEV_DATA_LOCATIONS);

        assertThatThrownBy(() -> DevLoginGuard.check(EMPTY, prod)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 設定がなければどの環境でも起動できる() {
        MockEnvironment prod = profiles("prod");

        assertThatCode(() -> DevLoginGuard.check(EMPTY, prod)).doesNotThrowAnyException();
    }
}
