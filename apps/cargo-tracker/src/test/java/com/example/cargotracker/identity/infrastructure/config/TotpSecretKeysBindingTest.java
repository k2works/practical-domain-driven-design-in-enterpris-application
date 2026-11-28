package com.example.cargotracker.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.infrastructure.crypto.TestSecretCiphers;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * TOTP の秘密の暗号化の鍵の結び付け（Bolt 31、SEC-16）。本番は環境変数で渡すので、環境変数の名前から
 * `cargotracker.totp-secret-keys` に結び付くことを確かめる（Bolt 31 計画の整合性検証の指摘）。
 */
class TotpSecretKeysBindingTest {

    private static StandardEnvironment environmentWith(org.springframework.core.env.PropertySource<?> source) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(source);
        return environment;
    }

    @Test
    void 環境変数CARGOTRACKER_TOTPSECRETKEYS_V1から版v1の鍵に結び付く() {
        // 環境変数の名前の読み替えは、名前が systemEnvironment の源にだけ当たる（本物の環境変数と同じ源に差し替える）
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("CARGOTRACKER_TOTPSECRETKEYS_V1", TestSecretCiphers.KEY_V1)));

        assertThat(IdentityConfiguration.totpSecretKeys(environment))
                .containsExactly(Map.entry("v1", TestSecretCiphers.KEY_V1));
    }

    @Test
    void プロパティの形でも結び付く() {
        StandardEnvironment environment = environmentWith(new MapPropertySource(
                "test-props", Map.of("cargotracker.totp-secret-keys.v1", TestSecretCiphers.KEY_V1)));

        assertThat(IdentityConfiguration.totpSecretKeys(environment))
                .containsExactly(Map.entry("v1", TestSecretCiphers.KEY_V1));
    }

    @Test
    void 鍵がなければ暗号化の部品を作らず起動を失敗させる() {
        StandardEnvironment environment = environmentWith(new MapPropertySource("empty", Map.of()));

        IdentityConfiguration configuration = new IdentityConfiguration();

        assertThatThrownBy(() -> configuration.totpSecretCipher(environment))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
