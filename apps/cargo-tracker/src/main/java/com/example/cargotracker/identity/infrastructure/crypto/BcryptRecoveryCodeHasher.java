package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 回復コードのハッシュの実装（Bolt 30、SEC-09）。password と同じ PasswordEncoder（bcrypt、強さ 10、`{bcrypt}` の
 * 接頭辞）で作り、照合する。回復コードは低エントロピーなので、速いハッシュ（SHA-256）にせず総当たりから守る。
 * ドメインは Spring Security の型を知らないため、ポートの実装として identity.infrastructure に置く（AT-01）。
 */
@Component
public class BcryptRecoveryCodeHasher implements RecoveryCodeHasher {

    private final PasswordEncoder passwordEncoder;

    public BcryptRecoveryCodeHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String code) {
        return passwordEncoder.encode(code);
    }

    @Override
    public boolean matches(String code, String hash) {
        return passwordEncoder.matches(code, hash);
    }
}
