package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 正規化した回復コード（Bolt 33d）。回復コードの形の知識をここだけに置く。 */
class NormalizedRecoveryCodeTest {

    @Test
    void 形の合わない値は作れずtoStringに平文を出さない() {
        assertThatThrownBy(() -> new NormalizedRecoveryCode("abcd-efgh")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NormalizedRecoveryCode("1A2B-3C4D")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NormalizedRecoveryCode("1a2b3c4d")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NormalizedRecoveryCode(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new NormalizedRecoveryCode("1a2b-3c4d").toString()).doesNotContain("1a2b");
    }

    @Test
    void 乱数の4バイトから発行の形を作り先頭の0を落とさない() {
        assertThat(NormalizedRecoveryCode.fromBytes(new byte[] {0x1a, 0x2b, 0x3c, 0x4d})
                        .value())
                .isEqualTo("1a2b-3c4d");
        assertThat(NormalizedRecoveryCode.fromBytes(new byte[] {0, 0, 0, (byte) 0xff})
                        .value())
                .isEqualTo("0000-00ff");
    }

    @Test
    void バイト数が4でなければ作らない() {
        assertThatThrownBy(() -> NormalizedRecoveryCode.fromBytes(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NormalizedRecoveryCode.fromBytes(new byte[3]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NormalizedRecoveryCode.fromBytes(new byte[5]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 区切りを入れ忘れた8文字は区切って読み形の違う文字列は空にする() {
        assertThat(NormalizedRecoveryCode.parse("1a2b3c4d"))
                .map(NormalizedRecoveryCode::value)
                .contains("1a2b-3c4d");
        assertThat(NormalizedRecoveryCode.parse("1a2b-3c4d"))
                .map(NormalizedRecoveryCode::value)
                .contains("1a2b-3c4d");
        assertThat(NormalizedRecoveryCode.parse(null)).isEmpty();
        assertThat(NormalizedRecoveryCode.parse("1a2b3c4")).as("7 文字").isEmpty();
        assertThat(NormalizedRecoveryCode.parse("1a2b3c4d5")).as("9 文字").isEmpty();
        assertThat(NormalizedRecoveryCode.parse("1a2-b3c4d")).as("区切りの位置が違う").isEmpty();
    }
}
