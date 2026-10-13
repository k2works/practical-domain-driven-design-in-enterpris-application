package com.example.cargotracker.identity.infrastructure.security;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * Base32（RFC 4648 の 6 節）。TOTP の秘密を認証アプリと受け渡す形（Bolt 31 の dev の入力済み、Bolt 31b の otpauth の URI と
 * 手で入力する秘密）。JDK にも java-otp にもないので自分で書き、RFC 4648 の試験ベクトルで確かめる（ADR-011）。
 * 復号は、埋め草（=）のない形・小文字・区切りの空白も受け付け、ほかの文字は失敗へ倒す。
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

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
        int padding = normalized.indexOf('=');
        if (padding >= 0) {
            if (!normalized.substring(padding).chars().allMatch(c -> c == '=')) {
                throw new IllegalArgumentException("Base32 の埋め草の後に文字がある");
            }
            normalized = normalized.substring(0, padding);
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
        return out.toByteArray();
    }
}
