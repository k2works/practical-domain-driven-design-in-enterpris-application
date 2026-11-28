package com.example.cargotracker.identity.infrastructure.crypto;

import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 再設定のトークンを {@link SecureRandom} の 32 バイトから作る（SEC-08。Bolt 33）。URL 安全な Base64（パディングなし）の 43 文字。
 */
@Component
public final class RandomPasswordResetTokenGenerator implements PasswordResetTokenGenerator {

    /** 乱数のバイト数（256 ビット）。 */
    static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
