package com.example.cargotracker.identity.infrastructure.crypto;

import java.util.Map;

/**
 * テストの TOTP の秘密の暗号化の鍵（Bolt 31）。`src/test/resources/config/application.properties` の
 * `cargotracker.totp-secret-keys.v1` と同じ値で、本物の秘密ではない。
 */
public final class TestSecretCiphers {

    /** 32 バイト（ASCII の "0123456789abcdef0123456789abcdef"）を Base64 にした鍵。 */
    public static final String KEY_V1 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private TestSecretCiphers() {}

    /** テストの鍵（版 v1）の暗号化の部品。 */
    public static AesGcmSecretCipher cipher() {
        return AesGcmSecretCipher.fromBase64Keys(Map.of("v1", KEY_V1));
    }
}
