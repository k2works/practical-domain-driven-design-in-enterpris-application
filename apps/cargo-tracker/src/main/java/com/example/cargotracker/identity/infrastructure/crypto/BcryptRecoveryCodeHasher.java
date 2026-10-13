package com.example.cargotracker.identity.infrastructure.crypto;

import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
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

    /**
     * 平文が保存したハッシュに一致するか。接頭辞（{@code {bcrypt}}）のない・知らないハッシュは、枠組み（DelegatingPasswordEncoder）が
     * {@link IllegalArgumentException} を投げるので、読めない要素として包み直す（Bolt 31d）。bcrypt の形の誤りは枠組みが false を返す。
     */
    @Override
    public boolean matches(String code, String hash) {
        try {
            return passwordEncoder.matches(code, hash);
        } catch (IllegalArgumentException e) {
            throw new UnreadableCredentialException("保存した回復コードのハッシュの形が違う", e);
        }
    }
}
