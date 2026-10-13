package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class BcryptRecoveryCodeHasherTest {

    private final BcryptRecoveryCodeHasher hasher =
            new BcryptRecoveryCodeHasher(PasswordEncoderFactories.createDelegatingPasswordEncoder());

    @Test
    void ハッシュはbcryptで平文を含まない() {
        String hash = hasher.hash("abcd-efgh");

        assertThat(hash).startsWith("{bcrypt}$2a$10$").doesNotContain("abcd-efgh");
    }

    @Test
    void 同じ平文でも塩で毎回違うハッシュになり両方に一致する() {
        String first = hasher.hash("abcd-efgh");
        String second = hasher.hash("abcd-efgh");

        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.matches("abcd-efgh", first)).isTrue();
        assertThat(hasher.matches("abcd-efgh", second)).isTrue();
    }

    @Test
    void 違う平文には一致しない() {
        assertThat(hasher.matches("abcd-efgx", hasher.hash("abcd-efgh"))).isFalse();
    }
}
