package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 書類の形式の判定（Q-INV-16）。拡張子でなく、中身の先頭のバイトで判定する。
 */
class DocumentMediaTypeTest {

    @Test
    void 先頭のバイトでPDFとPNGとJPEGを判定する() {
        assertThat(DocumentMediaType.detect("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)))
                .contains(DocumentMediaType.PDF);
        assertThat(DocumentMediaType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}))
                .contains(DocumentMediaType.PNG);
        assertThat(DocumentMediaType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1}))
                .contains(DocumentMediaType.JPEG);
    }

    @Test
    void 署名に当たらない中身と署名より短い中身と空の中身は判定できない() {
        assertThat(DocumentMediaType.detect("これは PDF ではない".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        assertThat(DocumentMediaType.detect("%PD".getBytes(StandardCharsets.US_ASCII)))
                .isEmpty();
        assertThat(DocumentMediaType.detect(new byte[0])).isEmpty();
    }

    @Test
    void 取得のときに返す形式の名前を持つ() {
        assertThat(DocumentMediaType.PDF.contentType()).isEqualTo("application/pdf");
        assertThat(DocumentMediaType.PNG.contentType()).isEqualTo("image/png");
        assertThat(DocumentMediaType.JPEG.contentType()).isEqualTo("image/jpeg");
    }
}
