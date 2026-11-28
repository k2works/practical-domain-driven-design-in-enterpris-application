package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 使えない password のハッシュの発行（Bolt 38b2。行 12、SEC-07）。平文は呼ぶたびに違う乱数で、ログインの照合と同じ PasswordEncoder で
 * ハッシュにする。
 */
class BcryptUnusablePasswordHashIssuerTest {

    private final PasswordEncoder delegating = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void 呼ぶたびに違う32バイト分の平文をハッシュにする() {
        List<String> plaintexts = new ArrayList<>();
        PasswordEncoder capturing = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                plaintexts.add(rawPassword.toString());
                return delegating.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return delegating.matches(rawPassword, encodedPassword);
            }
        };
        BcryptUnusablePasswordHashIssuer issuer = new BcryptUnusablePasswordHashIssuer(capturing);

        issuer.issue();
        issuer.issue();

        assertThat(plaintexts).hasSize(2);
        assertThat(plaintexts.get(0)).isNotEqualTo(plaintexts.get(1));
        assertThat(plaintexts).allSatisfy(plaintext -> assertThat(plaintext).hasSizeGreaterThanOrEqualTo(43));
    }

    @Test
    void ログインの照合と同じ形のハッシュで空の文字列とも一致しない() {
        String hash = new BcryptUnusablePasswordHashIssuer(delegating).issue();

        assertThat(hash).startsWith("{bcrypt}");
        assertThat(delegating.matches("", hash)).isFalse();
    }
}
