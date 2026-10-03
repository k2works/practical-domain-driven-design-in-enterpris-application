package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import java.util.Arrays;
import java.util.Objects;

/**
 * 取得した必要書類。書類の記録（ファイル名・形式など）と中身の組。
 *
 * @param document 必要書類
 * @param content 中身
 */
public record DocumentFile(RequiredDocument document, byte[] content) {

    public DocumentFile {
        Objects.requireNonNull(document, "document");
        content = Objects.requireNonNull(content, "content").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DocumentFile that
                && document.equals(that.document)
                && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(document, Arrays.hashCode(content));
    }

    @Override
    public String toString() {
        return "DocumentFile[document=" + document + ", size=" + content.length + "]";
    }
}
