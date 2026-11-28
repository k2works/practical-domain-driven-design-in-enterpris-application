package com.example.cargotracker.identity.infrastructure.crypto;

import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 再設定のトークンの SHA-256 のハッシュ（SEC-08。Bolt 33）。16 進の小文字 64 文字。
 */
@Component
public final class Sha256PasswordResetTokenHasher implements PasswordResetTokenHasher {

    @Override
    public String hash(String token) {
        Objects.requireNonNull(token, "token");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えない", e);
        }
    }
}
