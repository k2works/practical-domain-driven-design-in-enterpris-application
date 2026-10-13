package com.example.cargotracker.identity.infrastructure.crypto;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TOTP の生成器の java-otp の実装（ADR-011、Bolt 30）。HMAC-SHA1、30 秒の刻み、6 桁（US-18）。暗号化した秘密を受け、
 * 復号から平文の消去までをこの中で済ませる（Bolt 31）。
 * java-otp が担うのは HOTP の計算と切り詰めだけで、時間の窓の照合と再利用の拒否は {@link TotpCredential} が持つ。
 * 1 つの生成器を並行に使っても正しいコードを作ることを契約テストで確かめている（スパイクは synchronized で守った）。
 */
@Component
public final class JavaOtpTotpCodeGenerator implements TotpCodeGenerator {

    private static final String ALGORITHM = "HmacSHA1";

    private final TimeBasedOneTimePasswordGenerator generator;

    private final AesGcmSecretCipher cipher;

    /** 本番の桁数（6 桁）の生成器。 */
    @Autowired
    public JavaOtpTotpCodeGenerator(AesGcmSecretCipher cipher) {
        this(cipher, 6);
    }

    /** 桁数を指定した生成器（RFC 6238 の 8 桁の試験ベクトルの確かめに使う）。 */
    JavaOtpTotpCodeGenerator(AesGcmSecretCipher cipher, int digits) {
        this.cipher = cipher;
        try {
            this.generator = new TimeBasedOneTimePasswordGenerator(TotpCredential.STEP_DURATION, digits, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " を使えない", e);
        }
    }

    @Override
    public String code(UserId owner, EncryptedSecret encrypted, long step) {
        if (encrypted == null) {
            throw new IllegalArgumentException("TOTP の秘密がない");
        }
        if (step < 0) {
            throw new IllegalArgumentException("時間の刻みは 0 以上");
        }
        // 復号した平文はこのメソッドの中だけで使い、使い終えたら消す（Bolt 31、SEC-16）
        byte[] secret = cipher.decrypt(encrypted, owner);
        try {
            return generator.generateOneTimePasswordString(
                    new SecretKeySpec(secret, ALGORITHM),
                    Instant.ofEpochSecond(step * TotpCredential.STEP_DURATION.toSeconds()),
                    // 既定のロケールの数字（アラビア・インド数字など）にせず、ASCII の数字で書く
                    Locale.ROOT);
        } catch (InvalidKeyException e) {
            throw new UnreadableCredentialException("TOTP の秘密を使えない", e);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }
}
