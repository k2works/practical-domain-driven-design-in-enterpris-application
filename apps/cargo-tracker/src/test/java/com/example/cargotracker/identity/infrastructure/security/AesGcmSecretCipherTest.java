package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TOTP の秘密の暗号化（Bolt 31、SEC-16、ADR-011）。AES-GCM、96 ビットの IV を毎回作り、暗号文の先頭に鍵の版を持つ。
 * 知らない版・改ざん・形の誤りは失敗へ倒す（T-42）。
 */
class AesGcmSecretCipherTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    /** 版 v2 の鍵（32 バイトの 0x02）。 */
    private static final String KEY_V2 = Base64.getEncoder().encodeToString(filled((byte) 2));

    private static byte[] filled(byte value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        return key;
    }

    @Test
    void 暗号化して復号すると元の秘密に戻り暗号文は鍵の版で始まる() {
        AesGcmSecretCipher cipher = TestSecretCiphers.cipher();

        EncryptedSecret encrypted = cipher.encrypt(SECRET.clone());

        assertThat(encrypted.value()).startsWith("v1:");
        assertThat(encrypted.value()).doesNotContain("12345678901234567890");
        assertThat(cipher.decrypt(encrypted)).isEqualTo(SECRET);
    }

    @Test
    void 同じ秘密でも暗号化のたびに暗号文が違う() {
        AesGcmSecretCipher cipher = TestSecretCiphers.cipher();

        assertThat(cipher.encrypt(SECRET.clone())).isNotEqualTo(cipher.encrypt(SECRET.clone()));
    }

    @Test
    void 暗号文は表の列の長さに収まる() {
        EncryptedSecret encrypted = TestSecretCiphers.cipher().encrypt(SECRET.clone());

        assertThat(encrypted.value()).hasSize("v1:".length() + 64);
    }

    @Test
    void 暗号化は最新の版の鍵で行い復号は暗号文の版の鍵で行う() {
        AesGcmSecretCipher old = TestSecretCiphers.cipher();
        AesGcmSecretCipher rotated =
                AesGcmSecretCipher.fromBase64Keys(Map.of("v1", TestSecretCiphers.KEY_V1, "v2", KEY_V2));
        EncryptedSecret byOld = old.encrypt(SECRET.clone());

        EncryptedSecret byRotated = rotated.encrypt(SECRET.clone());

        assertThat(byRotated.value()).startsWith("v2:");
        assertThat(rotated.decrypt(byOld)).isEqualTo(SECRET);
        assertThat(rotated.decrypt(byRotated)).isEqualTo(SECRET);
    }

    @Test
    void 版の番号は数で比べ文字の順では比べない() {
        AesGcmSecretCipher cipher =
                AesGcmSecretCipher.fromBase64Keys(Map.of("v2", KEY_V2, "v10", TestSecretCiphers.KEY_V1));

        assertThat(cipher.encrypt(SECRET.clone()).value()).startsWith("v10:");
    }

    @Test
    void 知らない版と版のない暗号文は失敗へ倒す() {
        AesGcmSecretCipher cipher = TestSecretCiphers.cipher();
        String body = cipher.encrypt(SECRET.clone()).value().substring("v1:".length());

        EncryptedSecret unknownVersion = new EncryptedSecret("v2:" + body);
        EncryptedSecret noVersion = new EncryptedSecret(body);

        assertThatThrownBy(() -> cipher.decrypt(unknownVersion)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.decrypt(noVersion)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 改ざんした暗号文と別の鍵の暗号文は失敗へ倒す() {
        AesGcmSecretCipher cipher = TestSecretCiphers.cipher();
        String value = cipher.encrypt(SECRET.clone()).value();
        byte[] raw = Base64.getDecoder().decode(value.substring("v1:".length()));
        raw[raw.length - 1] ^= 1;
        EncryptedSecret tampered =
                new EncryptedSecret("v1:" + Base64.getEncoder().encodeToString(raw));
        AesGcmSecretCipher otherKey = AesGcmSecretCipher.fromBase64Keys(Map.of("v1", KEY_V2));

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalArgumentException.class);
        EncryptedSecret byOtherKey = new EncryptedSecret(value);
        assertThatThrownBy(() -> otherKey.decrypt(byOtherKey)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Base64でない暗号文と短すぎる暗号文は失敗へ倒す() {
        AesGcmSecretCipher cipher = TestSecretCiphers.cipher();

        EncryptedSecret notBase64 = new EncryptedSecret("v1:%%%");
        EncryptedSecret tooShort = new EncryptedSecret("v1:AAAA");

        assertThatThrownBy(() -> cipher.decrypt(notBase64)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.decrypt(tooShort)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 鍵がない設定と32バイトでない鍵と版の名前が違う設定は作れない() {
        Map<String, String> none = Map.of();
        Map<String, String> shortKey = Map.of("v1", Base64.getEncoder().encodeToString(new byte[16]));
        Map<String, String> longKey = Map.of("v1", Base64.getEncoder().encodeToString(new byte[33]));
        Map<String, String> badName = Map.of("key1", TestSecretCiphers.KEY_V1);
        Map<String, String> zeroVersion = Map.of("v0", TestSecretCiphers.KEY_V1);
        Map<String, String> notBase64 = Map.of("v1", "not base64!");

        assertThatThrownBy(() -> AesGcmSecretCipher.fromBase64Keys(none)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmSecretCipher.fromBase64Keys(shortKey))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmSecretCipher.fromBase64Keys(longKey))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmSecretCipher.fromBase64Keys(badName))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmSecretCipher.fromBase64Keys(zeroVersion))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmSecretCipher.fromBase64Keys(notBase64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 空の秘密は暗号化しない() {
        AesGcmSecretCipher cipher = TestSecretCiphers.cipher();
        byte[] empty = new byte[0];

        assertThatThrownBy(() -> cipher.encrypt(empty)).isInstanceOf(IllegalArgumentException.class);
    }
}
