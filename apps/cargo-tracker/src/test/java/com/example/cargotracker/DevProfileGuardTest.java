package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * ステージング・本番で dev プロファイルを起動時に拒否する（#48 K-03。W10 の Bolt 52-1）。
 * dev は H2 のインメモリで動き、データが失われるうえ、開発用の利用者が入る。環境の名前は Terraform が
 * {@code cargotracker.deployment.environment} で渡す。
 */
class DevProfileGuardTest {

    private static MockEnvironment environment(String deployment, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        if (deployment != null) {
            environment.setProperty(DevProfileGuard.DEPLOYMENT_PROPERTY, deployment);
        }
        return environment;
    }

    @ParameterizedTest(name = "{0} で dev を使うと起動を拒否する")
    @ValueSource(strings = {"staging", "production"})
    void ステージングと本番ではdevプロファイルを拒否する(String deployment) {
        DevProfileGuard guard = new DevProfileGuard(environment(deployment, "dev"));

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev")
                .hasMessageContaining(deployment);
    }

    @ParameterizedTest(name = "{0} で dev 以外は起動できる")
    @ValueSource(strings = {"staging", "production"})
    void ステージングと本番でもdev以外のプロファイルは通す(String deployment) {
        DevProfileGuard guard = new DevProfileGuard(environment(deployment, deployment));

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void 環境の名前がないとき_ローカルやテストでは_devを通す() {
        DevProfileGuard guard = new DevProfileGuard(environment(null, "dev"));

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void 知らない環境の名前は拒否する_打ち間違いで拒否が外れないようにする() {
        DevProfileGuard guard = new DevProfileGuard(environment("prod", "dev"));

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
        assertThat(DevProfileGuard.ENVIRONMENTS_ALLOWING_DEV).containsExactlyInAnyOrder("local", "demo");
    }

    @ParameterizedTest(name = "{0} では dev を通す")
    @ValueSource(strings = {"local", "demo"})
    void ローカルとデモではdevを通す(String deployment) {
        DevProfileGuard guard = new DevProfileGuard(environment(deployment, "dev"));

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }
}
