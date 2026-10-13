package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.UserId;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * TOTP の秘密の発行（Bolt 31b、US-18 AC7、ADR-011）。160 ビットの秘密を乱数で作って持ち主に結び付けて暗号化し、表示の値
 * （otpauth の URI、手入力のキー、QR のインラインの SVG）は暗号文から作る。平文の秘密はこの部品の外に出さない。
 */
class RandomTotpSecretIssuerTest {

    private static final UserId OWNER = new UserId(UUID.fromString("00000000-0000-0000-0000-00000000b001"));
    private static final UserId OTHER = new UserId(UUID.fromString("00000000-0000-0000-0000-00000000b002"));

    private final AesGcmSecretCipher cipher = TestSecretCiphers.cipher();
    private final RandomTotpSecretIssuer issuer = new RandomTotpSecretIssuer(cipher);

    @Test
    void 秘密は160ビットで持ち主に結び付いた暗号文になり発行のたびに違う() {
        EncryptedSecret first = issuer.issue(OWNER);
        EncryptedSecret second = issuer.issue(OWNER);

        assertThat(cipher.decrypt(first, OWNER)).hasSize(20);
        assertThat(cipher.decrypt(first, OWNER)).isNotEqualTo(cipher.decrypt(second, OWNER));
        assertThatThrownBy(() -> cipher.decrypt(first, OTHER)).isInstanceOf(UnreadableCredentialException.class);
    }

    @Test
    void 表示の値は認証アプリが読むotpauthのURIと4文字ずつ区切ったキー() {
        EncryptedSecret secret = issuer.issue(OWNER);
        String base32 = Base32.encode(cipher.decrypt(secret, OWNER)).replace("=", "");

        TotpProvisioning provisioning = issuer.provisioning(OWNER, secret, "shipper@example.com");

        assertThat(provisioning.otpauthUri())
                .isEqualTo("otpauth://totp/cargo-tracker:shipper%40example.com?secret=" + base32
                        + "&issuer=cargo-tracker&digits=6&period=30&algorithm=SHA1");
        assertThat(provisioning.manualKey().replace(" ", "")).isEqualTo(base32);
        assertThat(provisioning.manualKey()).matches("([A-Z2-7]{4} )*[A-Z2-7]{1,4}");
    }

    @Test
    void QRのSVGを読み戻すとURIに戻りstyleの属性を使わない() throws Exception {
        EncryptedSecret secret = issuer.issue(OWNER);
        TotpProvisioning provisioning = issuer.provisioning(OWNER, secret, "shipper@example.com");

        assertThat(provisioning.qrSvg())
                .startsWith("<svg")
                .contains("role=\"img\"")
                .contains("認証アプリで読み取る QR コード")
                .doesNotContain("style=");
        assertThat(decodeQr(provisioning.qrSvg())).isEqualTo(provisioning.otpauthUri());
    }

    @Test
    void 別の利用者の暗号文からは表示の値を作らない() {
        EncryptedSecret othersSecret = issuer.issue(OTHER);

        assertThatThrownBy(() -> issuer.provisioning(OWNER, othersSecret, "shipper@example.com"))
                .isInstanceOf(UnreadableCredentialException.class);
    }

    /** SVG の正方形のモジュール（rect）を画素にして、ZXing で読み戻す。 */
    private static String decodeQr(String svg) throws Exception {
        Matcher size = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        assertThat(size.find()).isTrue();
        int modules = Integer.parseInt(size.group(1));
        int scale = 4;
        int width = modules * scale;
        int[] pixels = new int[width * width];
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        Matcher rect = Pattern.compile("<rect x=\"(\\d+)\" y=\"(\\d+)\" width=\"1\" height=\"1\"")
                .matcher(svg);
        while (rect.find()) {
            int x = Integer.parseInt(rect.group(1));
            int y = Integer.parseInt(rect.group(2));
            for (int dy = 0; dy < scale; dy++) {
                for (int dx = 0; dx < scale; dx++) {
                    pixels[(y * scale + dy) * width + x * scale + dx] = 0xFF000000;
                }
            }
        }
        LuminanceSource source = new RGBLuminanceSource(width, width, pixels);
        // 余白のある素の画像なので、位置の検出をせずに読む（検出器は秘密によって 1% ほど読み損ねる。Bolt 32 で見つけた揺れ）
        return new QRCodeReader()
                .decode(
                        new BinaryBitmap(new HybridBinarizer(source)),
                        Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE))
                .getText();
    }
}
