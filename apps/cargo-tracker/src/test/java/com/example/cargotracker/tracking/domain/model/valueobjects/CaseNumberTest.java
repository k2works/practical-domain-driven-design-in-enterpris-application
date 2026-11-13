package com.example.cargotracker.tracking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 受付番号の形（Bolt 40-6。{@code SC-YYYY-NNNN}、表の列は 20 文字まで）。 */
class CaseNumberTest {

    @Test
    void 年と連番から4桁で埋めた受付番号を作る() {
        assertThat(CaseNumber.of(2026, 1).value()).isEqualTo("SC-2026-0001");
        assertThat(CaseNumber.of(2026, 12345).value()).isEqualTo("SC-2026-12345");
    }

    @Test
    void 年か連番が1未満なら拒む() {
        assertThatThrownBy(() -> CaseNumber.of(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CaseNumber.of(2026, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 空か長すぎる受付番号は拒む() {
        assertThatThrownBy(() -> new CaseNumber(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseNumber("x".repeat(21))).isInstanceOf(IllegalArgumentException.class);
    }
}
