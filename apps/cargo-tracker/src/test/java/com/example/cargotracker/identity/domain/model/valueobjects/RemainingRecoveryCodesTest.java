package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 残りの回復コード（Bolt 38d2 の行 1。SEC-10 の残りの回復コードの案内）。 */
class RemainingRecoveryCodesTest {

    @Test
    void 残り3個以下を少ないとし0個を残っていないとする() {
        assertThat(new RemainingRecoveryCodes(4).isLow()).as("4 個は少なくない").isFalse();
        assertThat(new RemainingRecoveryCodes(3).isLow()).as("3 個は少ない").isTrue();
        assertThat(new RemainingRecoveryCodes(1).isLow()).isTrue();
        assertThat(new RemainingRecoveryCodes(1).isEmpty()).isFalse();
        assertThat(new RemainingRecoveryCodes(0).isLow()).as("0 個も少ない").isTrue();
        assertThat(new RemainingRecoveryCodes(0).isEmpty()).isTrue();
    }

    @Test
    void 負の数は拒む() {
        assertThatThrownBy(() -> new RemainingRecoveryCodes(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
