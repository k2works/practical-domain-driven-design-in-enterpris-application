package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Base32（RFC 4648 の 6 節）。TOTP の秘密を認証アプリと受け渡す形（Bolt 31 の dev の入力済み、Bolt 31b の otpauth の URI）。
 * 試験ベクトルは RFC 4648 の 10 節。
 */
class Base32Test {

    @ParameterizedTest
    @CsvSource({
        "f, MY======",
        "fo, MZXQ====",
        "foo, MZXW6===",
        "foob, MZXW6YQ=",
        "fooba, MZXW6YTB",
        "foobar, MZXW6YTBOI======"
    })
    void RFC4648の試験ベクトルを符号化し復号する(String plain, String encoded) {
        byte[] bytes = plain.getBytes(StandardCharsets.US_ASCII);

        assertThat(Base32.encode(bytes)).isEqualTo(encoded);
        assertThat(Base32.decode(encoded)).isEqualTo(bytes);
    }

    @Test
    void RFC6238の秘密を復号する() {
        assertThat(Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
                .isEqualTo("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void 埋め草のない形と小文字と区切りの空白も復号する() {
        assertThat(Base32.decode("mzxw 6ytb oi")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void 空の文字列は空の配列() {
        assertThat(Base32.decode("")).isEmpty();
        assertThat(Base32.encode(new byte[0])).isEmpty();
    }

    @Test
    void Base32の文字でないものは失敗へ倒す() {
        assertThatThrownBy(() -> Base32.decode("MZXW1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base32.decode("MZ=XW")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base32.decode(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- 不正な長さ・余りのビット・埋め草の数（Bolt 32d。同じバイト列に 2 つの書き方を許さず、RFC 4648 で現れない形を拒む） ---

    @ParameterizedTest
    @CsvSource({"MY, f", "MZXQ, fo", "MZXW6, foo", "MZXW6YQ, foob", "MZXW6YTB, fooba", "MZXW6YTBOI, foobar"})
    void 余りが0と2と4と5と7の長さは埋め草がなくても復号する(String encoded, String plain) {
        assertThat(Base32.decode(encoded)).isEqualTo(plain.getBytes(StandardCharsets.US_ASCII));
    }

    @ParameterizedTest
    @CsvSource({"M", "MZX", "MZXW6Y", "MZX=====", "mzx w6y", "MZXW6YTBO"})
    void 余りが1と3と6の長さは失敗へ倒す(String encoded) {
        assertThatThrownBy(() -> Base32.decode(encoded)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 最後の文字の余りのビットが0でなければ失敗へ倒す() {
        assertThat(Base32.decode("MY")).isEqualTo("f".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> Base32.decode("MZ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Base32.decode("MZXQ")).isEqualTo("fo".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> Base32.decode("MZXR")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 埋め草を付けるなら全体が8の倍数の長さでなければ失敗へ倒す() {
        assertThat(Base32.decode("MY======")).isEqualTo("f".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> Base32.decode("MY=")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base32.decode("MY=======")).isInstanceOf(IllegalArgumentException.class);
    }
}
