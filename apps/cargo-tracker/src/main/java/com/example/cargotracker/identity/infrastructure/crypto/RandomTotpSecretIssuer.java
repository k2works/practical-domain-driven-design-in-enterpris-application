package com.example.cargotracker.identity.infrastructure.crypto;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.UserId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * TOTP の秘密の発行（Bolt 31b、US-18 AC7、ADR-011）。160 ビットの秘密を乱数で作り、持ち主に結び付けて暗号化する（AAD）。
 * 登録の表示の値（otpauth の URI、4 文字ずつ区切った手入力のキー、QR のインラインの SVG）は暗号文から作り、平文の秘密は
 * この部品の外に出さない（使い終えた配列は消す）。QR の SVG の組み立ては {@link QrCodeSvg} に任せる（Bolt 33d）。
 */
@Component
public final class RandomTotpSecretIssuer implements TotpSecretIssuer {

    /** 秘密の長さ（160 ビット。RFC 4226 の推奨、ADR-011）。 */
    private static final int SECRET_BYTES = 20;

    private static final String ISSUER = "cargo-tracker";

    private final AesGcmSecretCipher cipher;
    private final SecureRandom random = new SecureRandom();

    public RandomTotpSecretIssuer(AesGcmSecretCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public EncryptedSecret issue(UserId owner) {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        try {
            return cipher.encrypt(secret, owner);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Override
    public TotpProvisioning provisioning(UserId owner, EncryptedSecret secret, String accountName) {
        byte[] plain = cipher.decrypt(secret, owner);
        try {
            String base32 = Base32.encode(plain).replace("=", "");
            String uri = "otpauth://totp/" + ISSUER + ":" + encode(accountName) + "?secret=" + base32 + "&issuer="
                    + ISSUER + "&digits=" + TotpCredential.DIGITS + "&period="
                    + TotpCredential.STEP_DURATION.toSeconds()
                    + "&algorithm=" + JavaOtpTotpCodeGenerator.URI_ALGORITHM;
            return new TotpProvisioning(uri, grouped(base32), QrCodeSvg.of(uri));
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 手で入れやすいよう 4 文字ずつ空白で区切る。 */
    private static String grouped(String base32) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < base32.length(); i += 4) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(base32, i, Math.min(i + 4, base32.length()));
        }
        return out.toString();
    }
}
