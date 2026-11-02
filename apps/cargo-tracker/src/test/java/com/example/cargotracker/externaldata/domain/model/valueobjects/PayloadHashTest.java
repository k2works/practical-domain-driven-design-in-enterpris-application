package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 原本のハッシュ（Bolt 39 の行 8c）。 */
class PayloadHashTest {

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void バイト列のSHA256を16進64文字にする() {
        assertThat(PayloadHash.of(bytes("abc")).value())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void 同じバイト列なら同じハッシュで違うバイト列なら違うハッシュ() {
        assertThat(PayloadHash.of(bytes("V-1,1"))).isEqualTo(PayloadHash.of(bytes("V-1,1")));
        assertThat(PayloadHash.of(bytes("V-1,1"))).isNotEqualTo(PayloadHash.of(bytes("V-1,2")));
    }

    @Test
    void 形の違う文字列からハッシュを作れない() {
        assertThatThrownBy(() -> new PayloadHash("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PayloadHash("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PayloadHash(null)).isInstanceOf(NullPointerException.class);
    }
}
