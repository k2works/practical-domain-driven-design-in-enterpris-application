package com.example.cargotracker.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 公開の base URL の確かめ（メールのリンクの組み立ての元。SEC-08。Bolt 33）。 */
class PublicBaseUrlTest {

    private static MockEnvironment environment(String value, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        if (value != null) {
            environment.setProperty(IdentityConfiguration.PUBLIC_BASE_URL, value);
        }
        return environment;
    }

    @Test
    void 末尾の斜線を除いて返す() {
        assertThat(IdentityConfiguration.publicBaseUrl(environment("http://localhost:8080/")))
                .isEqualTo("http://localhost:8080");
        assertThat(IdentityConfiguration.publicBaseUrl(environment("https://cargo.example", "prod")))
                .isEqualTo("https://cargo.example");
    }

    @Test
    void 値がないか絶対のhttpのURLでなければ起動を止める() {
        assertThatThrownBy(() -> IdentityConfiguration.publicBaseUrl(environment(null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> IdentityConfiguration.publicBaseUrl(environment("/relative")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> IdentityConfiguration.publicBaseUrl(environment("javascript:alert(1)")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> IdentityConfiguration.publicBaseUrl(environment("https://cargo.example/?x=1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ステージングと本番ではhttpsでlocalhostでない値を求める() {
        assertThatThrownBy(() -> IdentityConfiguration.publicBaseUrl(environment("http://localhost:8080", "prod")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> IdentityConfiguration.publicBaseUrl(environment("https://localhost", "staging")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> IdentityConfiguration.publicBaseUrl(environment("http://cargo.example", "staging")))
                .isInstanceOf(IllegalStateException.class);
    }
}
