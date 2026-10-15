package com.example.cargotracker.identity.infrastructure.crypto;

import com.example.cargotracker.identity.domain.model.aggregates.PasswordHasher;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * password のハッシュの実装（Bolt 33a、SEC-07）。ログインの照合と同じ PasswordEncoder（DelegatingPasswordEncoder、bcrypt、強さ 10、
 * `{bcrypt}` の接頭辞）で作る。bcrypt は 72 バイトを超える password を受け付けないので、呼ぶ側が方針で先に確かめる（SEC-04）。
 */
@Component
public class BcryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BcryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String password) {
        return passwordEncoder.encode(Objects.requireNonNull(password, "password"));
    }
}
