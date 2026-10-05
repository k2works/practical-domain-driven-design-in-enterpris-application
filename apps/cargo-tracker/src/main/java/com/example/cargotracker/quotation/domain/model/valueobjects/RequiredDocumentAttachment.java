package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * 書類の添付。荷主が提出・出し直しで添付しようとする書類。書類の受付規則（Q-INV-16）を通り、保存してオブジェクトキーを得たら必要書類になる。
 *
 * @param type 書類の種類（荷主が選んだ入力欄）
 * @param fileName ファイル名（画面に出すだけに使う）
 * @param content 中身
 */
@ValueObject
public record RequiredDocumentAttachment(DocumentType type, String fileName, byte[] content) {

    public RequiredDocumentAttachment {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fileName, "fileName");
        content = Objects.requireNonNull(content, "content").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    /** 中身の大きさ（バイト）。 */
    public long size() {
        return content.length;
    }

    /** 中身の先頭のバイトから形式を判定する（Q-INV-16）。中身を複製しない（Bolt 6〜8 レビュー R-14）。 */
    public Optional<DocumentMediaType> detectMediaType() {
        return DocumentMediaType.detect(content);
    }

    /** 中身の SHA-256 を 16 進で返す。中身を複製しない（R-14）。 */
    public String sha256() {
        return RequiredDocument.sha256(content);
    }

    @Override
    public boolean equals(Object other) {
        return other
                        instanceof
                        RequiredDocumentAttachment(DocumentType otherType, String otherName, byte[] otherContent)
                && type == otherType
                && fileName.equals(otherName)
                && Arrays.equals(content, otherContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, fileName, Arrays.hashCode(content));
    }

    @Override
    public String toString() {
        return "RequiredDocumentAttachment[type=" + type + ", fileName=" + fileName + ", size=" + content.length + "]";
    }
}
