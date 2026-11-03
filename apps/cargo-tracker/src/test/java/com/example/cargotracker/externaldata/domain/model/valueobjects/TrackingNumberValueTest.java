package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 追跡番号の値（Bolt 39b2b）。追跡の追跡番号の形（CT と、紛らわしい文字を除いた英大文字・数字 12 桁）の写し。 */
class TrackingNumberValueTest {

    @Test
    void 行11_CTと英大文字と数字12桁の追跡番号を受ける() {
        assertThat(new TrackingNumberValue("CTABCDEFGH2345").value()).isEqualTo("CTABCDEFGH2345");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "CTABCDEFGH2340", // 0
                "CTABCDEFGH234O", // O
                "CTABCDEFGH2341", // 1
                "CTABCDEFGH234I", // I
                "CTABCDEFGH234L", // L
                "CTABCDEFGH234", // 13 文字
                "CTABCDEFGH23456", // 15 文字
                "ctABCDEFGH2345", // 小文字の接頭辞
                "CXABCDEFGH2345" // 接頭辞の違い
            })
    void 行11_紛らわしい文字と長さの違いと接頭辞の違いの追跡番号は作れない(String value) {
        assertThatThrownBy(() -> new TrackingNumberValue(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 行24_追跡番号の値はNULLを拒む() {
        assertThatThrownBy(() -> new TrackingNumberValue(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 行11_追跡番号の形の誤りは行の誤りでどうすればよいかを業務の言葉で示す() {
        FormatDefect defect = FormatDefect.INVALID_TRACKING_NUMBER;

        assertThat(defect.occursIn(FormatDefect.Extent.ROW)).isTrue();
        assertThat(defect.occursIn(FormatDefect.Extent.FILE)).isFalse();
        assertThat(defect.rowNote(new PhysicalLineRange(12, 12), List.of("tracking_number")))
                .isEqualTo("12 行目: 追跡番号の形が違います（tracking_number）。CT と英大文字・数字 12 桁（0・O・1・I・L を除く）で書いてください。");
    }
}
