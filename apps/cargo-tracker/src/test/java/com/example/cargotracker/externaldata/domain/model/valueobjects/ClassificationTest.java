package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 分類の規則（Bolt 39b の行 1〜3。ED-INV-02、BR-12）。既にある外部原本の最初のハッシュと今のハッシュから分類を決める。 */
class ClassificationTest {

    private static final PayloadHash FIRST = PayloadHash.of("MS-0001,1,TRK-0001".getBytes(StandardCharsets.UTF_8));
    private static final PayloadHash CHANGED = PayloadHash.of("MS-0001,1,TRK-0002".getBytes(StandardCharsets.UTF_8));

    @Test
    void 行1_外部原本のない冪等性キーは採用() {
        assertThat(Classification.classify(Optional.empty(), FIRST)).isEqualTo(Classification.ADOPTED);
    }

    @Test
    void 行2_最初のハッシュと同じなら重複() {
        assertThat(Classification.classify(Optional.of(FIRST), FIRST)).isEqualTo(Classification.DUPLICATE);
    }

    @Test
    void 行3_最初のハッシュと違えば隔離() {
        assertThat(Classification.classify(Optional.of(FIRST), CHANGED)).isEqualTo(Classification.QUARANTINED);
    }
}
