package com.example.cargotracker.quotation.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 必要書類の画面の部品（ファイル名の整え方と大きさの表示）。
 */
class RequiredDocumentViewsTest {

    @Test
    void ファイル名はパスの部分と制御文字を除き255文字までにする() {
        assertThat(RequiredDocumentViews.fileName("C:\\Users\\shipper\\invoice.pdf"))
                .isEqualTo("invoice.pdf");
        assertThat(RequiredDocumentViews.fileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(RequiredDocumentViews.fileName("in\u0000voice\n.pdf")).isEqualTo("invoice.pdf");
        assertThat(RequiredDocumentViews.fileName("   ")).isEqualTo("document");
        assertThat(RequiredDocumentViews.fileName("あ".repeat(300))).hasSize(255);
    }

    @Test
    void 大きさはKBを切り上げMBは小数点以下1桁で示す() {
        assertThat(RequiredDocumentViews.size(1)).isEqualTo("1 KB");
        assertThat(RequiredDocumentViews.size(120 * 1024)).isEqualTo("120 KB");
        assertThat(RequiredDocumentViews.size(1024 * 1024 + 512 * 1024)).isEqualTo("1.5 MB");
    }
}
