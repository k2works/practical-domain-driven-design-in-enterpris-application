package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 冪等性キー（Bolt 39b の行 7。BR-12）。提供元・原本種別・外部 ID・外部版の 4 つで同一性を判定する。 */
class IdempotencyKeyTest {

    @Test
    void 行7_4つの値が同じなら同じキー() {
        assertThat(new IdempotencyKey("運送会社A", "MILESTONE", "MS-0001", "1"))
                .isEqualTo(new IdempotencyKey("運送会社A", "MILESTONE", "MS-0001", "1"));
    }

    @Test
    void 行7_提供元か原本種別か外部版が違えば別のキー() {
        IdempotencyKey key = new IdempotencyKey("運送会社A", "MILESTONE", "MS-0001", "1");

        assertThat(key).isNotEqualTo(new IdempotencyKey("運送会社B", "MILESTONE", "MS-0001", "1"));
        assertThat(key).isNotEqualTo(new IdempotencyKey("運送会社A", "VOYAGE", "MS-0001", "1"));
        assertThat(key).isNotEqualTo(new IdempotencyKey("運送会社A", "MILESTONE", "MS-0001", "2"));
    }

    @Test
    void 空の値ではキーを作れない() {
        assertThatThrownBy(() -> new IdempotencyKey("運送会社A", "MILESTONE", " ", "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
