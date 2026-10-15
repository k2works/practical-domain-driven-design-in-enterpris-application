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

    private static void assertRejected(MockEnvironment environment) {
        assertThatThrownBy(() -> IdentityConfiguration.publicBaseUrl(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 値がないか絶対のhttpのURLでなければ起動を止める() {
        assertRejected(environment(null));
        assertRejected(environment("/relative"));
        assertRejected(environment("javascript:alert(1)"));
        assertRejected(environment("https://cargo.example/?x=1"));
        assertRejected(environment("https://user:secret@cargo.example"));
    }

    @Test
    void ステージングと本番ではhttpsでループバックでない値を求める() {
        assertRejected(environment("http://localhost:8080", "prod"));
        assertRejected(environment("https://localhost", "staging"));
        assertRejected(environment("https://127.0.0.1", "prod"));
        assertRejected(environment("https://[::1]", "prod"));
        assertRejected(environment("http://cargo.example", "staging"));
    }
}
