package com.example.cargotracker.identity.infrastructure.security;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * TOTP の生成器の java-otp の実装（ADR-011、Bolt 30）。HMAC-SHA1、30 秒の刻み、6 桁（US-18）。
 * java-otp が担うのは HOTP の計算と切り詰めだけで、時間の窓の照合と再利用の拒否は {@link TotpCredential} が持つ。
 * 1 つの生成器を並行に使っても正しいコードを作ることを契約テストで確かめている（スパイクは synchronized で守った）。
 */
@Component
public final class JavaOtpTotpCodeGenerator implements TotpCodeGenerator {

    private static final String ALGORITHM = "HmacSHA1";

    private final TimeBasedOneTimePasswordGenerator generator;

    /** 本番の桁数（6 桁）の生成器。 */
    public JavaOtpTotpCodeGenerator() {
        this(6);
    }

    /** 桁数を指定した生成器（RFC 6238 の 8 桁の試験ベクトルの確かめに使う）。 */
    JavaOtpTotpCodeGenerator(int digits) {
        try {
            this.generator = new TimeBasedOneTimePasswordGenerator(TotpCredential.STEP_DURATION, digits, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " を使えない", e);
        }
    }

    @Override
    public String code(byte[] secret, long step) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("TOTP の秘密がない");
        }
        if (step < 0) {
            throw new IllegalArgumentException("時間の刻みは 0 以上");
        }
        try {
            return generator.generateOneTimePasswordString(
                    new SecretKeySpec(secret, ALGORITHM),
                    Instant.ofEpochSecond(step * TotpCredential.STEP_DURATION.toSeconds()));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("TOTP の秘密を使えない", e);
        }
    }
}
