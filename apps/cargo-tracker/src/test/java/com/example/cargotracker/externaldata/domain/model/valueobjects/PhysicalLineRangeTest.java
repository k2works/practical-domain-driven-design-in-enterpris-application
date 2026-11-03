package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 物理行の範囲（Bolt 39b2 の開発レビュー）。行と誤りの始まりと終わりから物理行の数と注記の範囲の言葉を作る。 */
class PhysicalLineRangeTest {

    @Test
    void 行15_物理行の数は始まりから終わりまでの行を両端を含めて数える() {
        assertThat(new PhysicalLineRange(12, 12).lineCount()).isEqualTo(1);
        assertThat(new PhysicalLineRange(2, 3).lineCount()).isEqualTo(2);
    }

    @Test
    void 行4_注記の範囲の言葉は1行なら行目でまたがれば始まりから終わりまで() {
        assertThat(new PhysicalLineRange(12, 12).wording()).isEqualTo("12 行目");
        assertThat(new PhysicalLineRange(12, 13).wording()).isEqualTo("12 行目から 13 行目まで");
    }

    @Test
    void 行15_始まりが1より前か終わりが始まりより前の範囲は作れない() {
        assertThatThrownBy(() -> new PhysicalLineRange(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PhysicalLineRange(4, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
