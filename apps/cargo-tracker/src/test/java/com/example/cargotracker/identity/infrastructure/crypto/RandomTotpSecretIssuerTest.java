package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.UserId;
import java.util.UUID;
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
    void QRのSVGを読み戻すとURIに戻る() throws Exception {
        // SVG の形（role・title・style を使わない）は QrCodeSvgTest で確かめる（Bolt 33d で分けた）
        EncryptedSecret secret = issuer.issue(OWNER);
        TotpProvisioning provisioning = issuer.provisioning(OWNER, secret, "shipper@example.com");

        assertThat(QrCodeSvgTest.decodeQr(provisioning.qrSvg())).isEqualTo(provisioning.otpauthUri());
    }

    @Test
    void 別の利用者の暗号文からは表示の値を作らない() {
        EncryptedSecret othersSecret = issuer.issue(OTHER);

        assertThatThrownBy(() -> issuer.provisioning(OWNER, othersSecret, "shipper@example.com"))
                .isInstanceOf(UnreadableCredentialException.class);
    }
}
