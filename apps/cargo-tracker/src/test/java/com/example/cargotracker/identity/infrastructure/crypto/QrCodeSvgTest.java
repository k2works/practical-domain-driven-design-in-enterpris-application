package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * QR コードのインラインの SVG（Bolt 33d で秘密の発行から分けた）。CSP のもとで描けるよう style の属性を使わず、読み戻すと
 * 渡した文字列に戻る。
 */
class QrCodeSvgTest {

    private static final String URI =
            "otpauth://totp/cargo-tracker:shipper%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=cargo-tracker";

    @Test
    void SVGを読み戻すと渡した文字列に戻りstyleの属性を使わない() throws Exception {
        String svg = QrCodeSvg.of(URI);

        assertThat(svg)
                .startsWith("<svg")
                .contains("role=\"img\"")
                .contains("認証アプリで読み取る QR コード")
                .doesNotContain("style=");
        assertThat(decodeQr(svg)).isEqualTo(URI);
    }

    /** SVG の正方形のモジュール（rect）を画素にして、ZXing で読み戻す。 */
    static String decodeQr(String svg) throws Exception {
        Matcher size = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        assertThat(size.find()).isTrue();
        int modules = Integer.parseInt(size.group(1));
        int scale = 4;
        int width = modules * scale;
        int[] pixels = new int[width * width];
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        Matcher rect = Pattern.compile("<rect x=\"(\\d+)\" y=\"(\\d+)\" width=\"1\" height=\"1\"")
                .matcher(svg);
        while (rect.find()) {
            int x = Integer.parseInt(rect.group(1));
            int y = Integer.parseInt(rect.group(2));
            for (int dy = 0; dy < scale; dy++) {
                for (int dx = 0; dx < scale; dx++) {
                    pixels[(y * scale + dy) * width + x * scale + dx] = 0xFF000000;
                }
            }
        }
        LuminanceSource source = new RGBLuminanceSource(width, width, pixels);
        // 余白のある素の画像なので、位置の検出をせずに読む（検出器は秘密によって 1% ほど読み損ねる。Bolt 32 で見つけた揺れ）
        return new QRCodeReader()
                .decode(
                        new BinaryBitmap(new HybridBinarizer(source)),
                        Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE))
                .getText();
    }
}
