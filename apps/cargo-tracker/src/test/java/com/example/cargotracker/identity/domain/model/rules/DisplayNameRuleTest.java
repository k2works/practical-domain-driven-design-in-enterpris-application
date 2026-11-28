package com.example.cargotracker.identity.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 表示名の規則（Bolt 38b3・38b3b）。登録のサービスの表示名のテストと同じ値を、規則の口で確かめる。 */
class DisplayNameRuleTest {

    @Test
    void 前後のUnicodeの空白を除いた名前を返す() {
        assertThat(DisplayNameRule.normalize("\u00A0" + "\u3000" + "新人 花子" + "\u202F"))
                .contains("新人 花子");
    }

    @Test
    void 二百コードポイントまで受け付け二百一を拒む() {
        String max = "\uD842\uDFB7".repeat(200);

        assertThat(DisplayNameRule.normalize(max)).contains(max);
        assertThat(DisplayNameRule.normalize("\u00A0" + max + "\u202F")).contains(max);
        assertThat(DisplayNameRule.normalize(max + "あ")).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] 見分けにくい表示名を拒む")
    @ValueSource(strings = {"", "   ", "\u00A0\u00A0", "新人\u0007花子", "新人\u202E花子", "新人\u2028花子", "新人\uDB40\uDC61花子"})
    void 空や見分けにくい名前を拒む(String displayName) {
        assertThat(DisplayNameRule.normalize(displayName)).isEmpty();
    }

    @Test
    void nullは空として拒む() {
        assertThat(DisplayNameRule.normalize(null)).isEmpty();
    }
}
