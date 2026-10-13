package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP の秘密の暗号化（Bolt 31、SEC-16、ADR-011）。AES-GCM（256 ビットの鍵、96 ビットの IV を毎回乱数で作る、128 ビットのタグ）。
 * 暗号文は {@code v<版>:<Base64(IV + 暗号文 + タグ)>} の形で、先頭に鍵の版を持つ。暗号化は最新の版の鍵で行い、復号は暗号文の版の
 * 鍵で行う（鍵の入れ替えに備える）。知らない版・改ざん・形の誤りは失敗へ倒す（T-42）。平文の秘密はこの部品と
 * {@link JavaOtpTotpCodeGenerator} の中だけで扱い、使い終えたら呼び出し側が消す（ArchUnit で `identity.infrastructure` に閉じる）。
 */
public final class AesGcmSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Pattern VERSION = Pattern.compile("v([1-9][0-9]{0,8})");
    private static final Pattern ENCRYPTED = Pattern.compile("(v[1-9][0-9]{0,8}):(.+)");

    private final Map<String, SecretKey> keys;
    private final String currentVersion;
    private final SecureRandom random = new SecureRandom();

    private AesGcmSecretCipher(Map<String, SecretKey> keys, String currentVersion) {
        this.keys = Map.copyOf(keys);
        this.currentVersion = currentVersion;
    }

    /**
     * 版ごとの鍵（Base64 の 32 バイト）から作る。版の名前は {@code v1}・{@code v2} …で、番号の最も大きい版で暗号化する。
     *
     * @throws IllegalArgumentException 鍵がない、版の名前が違う、Base64 でない、32 バイトでない
     */
    public static AesGcmSecretCipher fromBase64Keys(Map<String, String> base64Keys) {
        if (base64Keys == null || base64Keys.isEmpty()) {
            throw new IllegalArgumentException("TOTP の秘密の暗号化の鍵がない（cargotracker.totp-secret-keys.v1）");
        }
        Map<String, SecretKey> keys = new TreeMap<>();
        for (Map.Entry<String, String> entry : base64Keys.entrySet()) {
            String version = entry.getKey();
            if (!VERSION.matcher(version).matches()) {
                throw new IllegalArgumentException("鍵の版の名前は v1・v2 …の形: " + version);
            }
            keys.put(version, new SecretKeySpec(decodeKey(version, entry.getValue()), "AES"));
        }
        String current = keys.keySet().stream()
                .max(Comparator.comparingLong(AesGcmSecretCipher::number))
                .orElseThrow();
        return new AesGcmSecretCipher(keys, current);
    }

    private static byte[] decodeKey(String version, String base64) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("鍵 " + version + " が Base64 でない", e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalArgumentException("鍵 " + version + " は " + KEY_BYTES + " バイト（256 ビット）にする");
        }
        return key;
    }

    private static long number(String version) {
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("鍵の版の名前は v1・v2 …の形: " + version);
        }
        return Long.parseLong(matcher.group(1));
    }

    /** 最新の版の鍵で暗号化する。平文の配列は呼び出し側が使い終えたら消す。 */
    public EncryptedSecret encrypt(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("暗号化する秘密がない");
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentVersion), new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext);
            byte[] body = ByteBuffer.allocate(IV_BYTES + sealed.length)
                    .put(iv)
                    .put(sealed)
                    .array();
            return new EncryptedSecret(
                    currentVersion + ":" + Base64.getEncoder().encodeToString(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP の秘密を暗号化できない", e);
        }
    }

    /**
     * 暗号文の版の鍵で復号する。返した配列は呼び出し側が使い終えたら消す（{@link Arrays#fill(byte[], byte)}）。
     *
     * @throws IllegalArgumentException 版がない・知らない版、Base64 でない、短すぎる、改ざん・別の鍵
     */
    public byte[] decrypt(EncryptedSecret encrypted) {
        if (encrypted == null) {
            throw new IllegalArgumentException("暗号化した秘密がない");
        }
        Matcher matcher = ENCRYPTED.matcher(encrypted.value());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("暗号化した秘密に鍵の版がない");
        }
        SecretKey key = keys.get(matcher.group(1));
        if (key == null) {
            throw new IllegalArgumentException("知らない鍵の版: " + matcher.group(1));
        }
        byte[] body;
        try {
            body = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("暗号化した秘密が Base64 でない", e);
        }
        if (body.length <= IV_BYTES + TAG_BITS / 8) {
            throw new IllegalArgumentException("暗号化した秘密が短すぎる");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, body, 0, IV_BYTES));
            return cipher.doFinal(body, IV_BYTES, body.length - IV_BYTES);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new IllegalArgumentException("暗号化した秘密を復号できない（改ざんか別の鍵）", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP の秘密を復号できない", e);
        }
    }
}
