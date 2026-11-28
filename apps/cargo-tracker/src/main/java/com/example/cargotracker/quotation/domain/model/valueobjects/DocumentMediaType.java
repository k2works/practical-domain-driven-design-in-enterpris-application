package com.example.cargotracker.quotation.domain.model.valueobjects;

import java.util.Arrays;
import java.util.Optional;

/**
 * 書類の形式。拡張子でなく、中身の先頭のバイト（署名）で判定する（Q-INV-16）。値の名前はデータモデルの値と同じにする。
 */
public enum DocumentMediaType {
    /** PDF（{@code %PDF-} で始まる）。 */
    PDF("application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
    /** PNG（8 バイトの署名で始まる）。 */
    PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    /** JPEG（{@code FF D8 FF} で始まる）。 */
    JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    private final String contentType;
    private final byte[] signature;

    DocumentMediaType(String contentType, byte[] signature) {
        this.contentType = contentType;
        this.signature = signature;
    }

    /** 取得のときに返す形式の名前（Content-Type）。 */
    public String contentType() {
        return contentType;
    }

    /** 中身の先頭のバイトから形式を判定する。どの署名にも当たらなければ空。 */
    public static Optional<DocumentMediaType> detect(byte[] content) {
        return Arrays.stream(values()).filter(type -> type.matches(content)).findFirst();
    }

    private boolean matches(byte[] content) {
        return content.length >= signature.length
                && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }
}
