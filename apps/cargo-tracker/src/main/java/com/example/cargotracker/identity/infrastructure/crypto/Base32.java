package com.example.cargotracker.identity.infrastructure.crypto;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Set;

/**
 * Base32（RFC 4648 の 6 節）。TOTP の秘密を認証アプリと受け渡す形（Bolt 31 の dev の入力済み、Bolt 31b の otpauth の URI と
 * 手で入力する秘密）。JDK にも java-otp にもないので自分で書き、RFC 4648 の試験ベクトルで確かめる（ADR-011）。
 * 復号は、埋め草（=）のない形・小文字・区切りの半角スペースも受け付け、ほかの文字は失敗へ倒す。RFC 4648 で現れない長さ（8 で割った
 * 余りが 1・3・6）、最後の文字の余りのビットが 0 でないもの（同じバイト列の 2 つ目の書き方）、データの長さに合わない数の埋め草も
 * 失敗へ倒す（Bolt 32d）。
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /** 8 文字の組の中で、データの文字数として現れない余り（RFC 4648 の 6 節）。 */
    private static final Set<Integer> INVALID_REMAINDERS = Set.of(1, 3, 6);

    private Base32() {}

    public static String encode(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
        }
        while (out.length() % 8 != 0) {
            out.append('=');
        }
        return out.toString();
    }

    public static byte[] decode(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Base32 の文字列がない");
        }
        String normalized = text.replace(" ", "").toUpperCase(Locale.ROOT);
        normalized = withoutPadding(normalized);
        if (INVALID_REMAINDERS.contains(normalized.length() % 8)) {
            throw new IllegalArgumentException("Base32 の長さが正しくない");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : normalized.toCharArray()) {
            int value = ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Base32 の文字でない: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        if ((buffer & ((1 << bits) - 1)) != 0) {
            throw new IllegalArgumentException("Base32 の最後の文字の余りのビットが 0 でない");
        }
        return out.toByteArray();
    }

    /** 末尾の埋め草（=）を除く。埋め草はデータの文字を 8 の倍数にそろえる数だけ（多すぎる・足りない・データのない埋め草を許さない）。 */
    private static String withoutPadding(String normalized) {
        int padding = normalized.indexOf('=');
        if (padding < 0) {
            return normalized;
        }
        if (!normalized.substring(padding).chars().allMatch(c -> c == '=')) {
            throw new IllegalArgumentException("Base32 の埋め草の後に文字がある");
        }
        if (padding == 0 || normalized.length() != (padding + 7) / 8 * 8) {
            throw new IllegalArgumentException("Base32 の埋め草の数がデータの長さに合わない");
        }
        return normalized.substring(0, padding);
    }
}
