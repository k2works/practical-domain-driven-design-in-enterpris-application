package com.example.cargotracker.identity.infrastructure.crypto;

import com.example.cargotracker.identity.domain.model.aggregates.UnusablePasswordHashIssuer;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 使えない password のハッシュの実装（Bolt 38b2、SEC-07）。{@link SecureRandom} の 32 バイトを Base64 にした平文を、ログインの照合と同じ
 * PasswordEncoder（DelegatingPasswordEncoder、bcrypt、強さ 10、`{bcrypt}` の接頭辞）でハッシュにし、平文は捨てる。
 */
@Component
public class BcryptUnusablePasswordHashIssuer implements UnusablePasswordHashIssuer {

    private static final int BYTES = 32;

    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public BcryptUnusablePasswordHashIssuer(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String issue() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return passwordEncoder.encode(Base64.getEncoder().encodeToString(bytes));
    }
}
