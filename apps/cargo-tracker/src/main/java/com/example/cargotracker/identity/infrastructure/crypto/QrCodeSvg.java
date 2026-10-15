package com.example.cargotracker.identity.infrastructure.crypto;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

/**
 * QR コードのインラインの SVG（Bolt 31b で秘密の発行の中に作り、Bolt 33d で分けた）。ZXing で行列を作り、CSP
 * （{@code default-src 'self'}）のもとで描けるよう {@code style} を使わず {@code fill} の属性で色を付ける。秘密を知らず、渡された
 * 文字列（otpauth の URI）を描くだけにする。
 */
final class QrCodeSvg {

    /** QR の周りの余白（モジュールの数。仕様の最小は 4）。 */
    private static final int QUIET_ZONE = 4;

    private QrCodeSvg() {}

    /** QR の行列を、暗いモジュールごとに 1 単位の正方形を置いたインラインの SVG にする。 */
    static String of(String text) {
        QRCode code;
        try {
            // URI は URL エンコードした ASCII だけなので文字セットを指定しない（ECI を付けると読めない認証アプリがある。Bolt 31b の開発レビュー）
            code = Encoder.encode(text, ErrorCorrectionLevel.M);
        } catch (WriterException e) {
            throw new IllegalStateException("QR コードを作れない", e);
        }
        ByteMatrix matrix = code.getMatrix();
        int size = matrix.getWidth() + QUIET_ZONE * 2;
        StringBuilder svg = new StringBuilder()
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(size)
                .append(' ')
                .append(size)
                .append("\" width=\"240\" height=\"240\" role=\"img\" shape-rendering=\"crispEdges\">")
                .append("<title>認証アプリで読み取る QR コード</title>")
                .append("<rect x=\"0\" y=\"0\" width=\"")
                .append(size)
                .append("\" height=\"")
                .append(size)
                .append("\" fill=\"#ffffff\"/>");
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y) == 1) {
                    svg.append("<rect x=\"")
                            .append(x + QUIET_ZONE)
                            .append("\" y=\"")
                            .append(y + QUIET_ZONE)
                            .append("\" width=\"1\" height=\"1\" fill=\"#000000\"/>");
                }
            }
        }
        return svg.append("</svg>").toString();
    }
}
