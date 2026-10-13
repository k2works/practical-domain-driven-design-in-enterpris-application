package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.UserId;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;

/**
 * TOTP の秘密の発行（Bolt 31b、US-18 AC7、ADR-011）。160 ビットの秘密を乱数で作り、持ち主に結び付けて暗号化する（AAD）。
 * 登録の表示の値（otpauth の URI、4 文字ずつ区切った手入力のキー、QR のインラインの SVG）は暗号文から作り、平文の秘密は
 * この部品の外に出さない（使い終えた配列は消す）。QR は ZXing で行列を作り、CSP（{@code default-src 'self'}）のもとで描けるよう
 * {@code style} を使わず {@code fill} の属性で色を付ける。
 */
public final class RandomTotpSecretIssuer implements TotpSecretIssuer {

    /** 秘密の長さ（160 ビット。RFC 4226 の推奨、ADR-011）。 */
    private static final int SECRET_BYTES = 20;

    private static final String ISSUER = "cargo-tracker";

    /** QR の周りの余白（モジュールの数。仕様の最小は 4）。 */
    private static final int QUIET_ZONE = 4;

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
                    + ISSUER + "&digits=6&period=" + TotpCredential.STEP_DURATION.toSeconds() + "&algorithm=SHA1";
            return new TotpProvisioning(uri, grouped(base32), qrSvg(uri));
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

    /** QR の行列を、暗いモジュールごとに 1 単位の正方形を置いたインラインの SVG にする。 */
    private static String qrSvg(String text) {
        QRCode code;
        try {
            code = Encoder.encode(
                    text, ErrorCorrectionLevel.M, Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()));
        } catch (WriterException e) {
            throw new IllegalStateException("QR コードを作れない", e);
        }
        ByteMatrix matrix = code.getMatrix();
        int size = matrix.getWidth() + QUIET_ZONE * 2;
        StringBuilder svg = new StringBuilder()
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(size)
                .append(' ')
                .append(size)
                .append("\" width=\"240\" height=\"240\" role=\"img\" shape-rendering=\"crispEdges\">")
                .append("<title>認証アプリで読み取る QR コード</title>")
                .append("<rect x=\"0\" y=\"0\" width=\"")
                .append(size)
                .append("\" height=\"")
                .append(size)
                .append("\" fill=\"#ffffff\"/>");
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y) == 1) {
                    svg.append("<rect x=\"")
                            .append(x + QUIET_ZONE)
                            .append("\" y=\"")
                            .append(y + QUIET_ZONE)
                            .append("\" width=\"1\" height=\"1\" fill=\"#000000\"/>");
                }
            }
        }
        return svg.append("</svg>").toString();
    }
}
