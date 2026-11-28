package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 必要書類。輸送条件に添付した書類 1 件で、版に付き、提出後は変えない（2026-10-03 の D-20。Bolt 7）。
 * 中身は書類の保存（{@code RequiredDocumentStorage}）に置き、ここにはオブジェクトキーと SHA-256 を持つ。
 *
 * @param documentNo 書類番号（版の中で 1 から。出し直しでは新しい版の中で 1 から振り直す。D-25）
 * @param type 書類の種類
 * @param fileName ファイル名（画面に出すだけに使い、オブジェクトキーには使わない）
 * @param mediaType 形式
 * @param sizeBytes 大きさ（バイト）
 * @param sha256 中身の SHA-256（16 進 64 文字）
 * @param objectKey オブジェクトキー
 */
@ValueObject
public record RequiredDocument(
        int documentNo,
        DocumentType type,
        String fileName,
        DocumentMediaType mediaType,
        long sizeBytes,
        String sha256,
        String objectKey) {

    public RequiredDocument {
        if (documentNo < 1) {
            throw new IllegalArgumentException("書類番号は 1 以上です: " + documentNo);
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(objectKey, "objectKey");
    }

    /**
     * 受付規則を通った添付を、判定した形式と保存したオブジェクトキーとともに必要書類にする。大きさと SHA-256 は中身から求める。
     */
    public static RequiredDocument of(
            int documentNo, RequiredDocumentAttachment attachment, DocumentMediaType mediaType, String objectKey) {
        return new RequiredDocument(
                documentNo,
                attachment.type(),
                attachment.fileName(),
                mediaType,
                attachment.size(),
                attachment.sha256(),
                objectKey);
    }

    /** 書類番号だけを変えた必要書類（出し直しで引き継ぐ書類に、新しい版の番号を振る。D-25）。オブジェクトキーは変えない。 */
    public RequiredDocument withDocumentNo(int newDocumentNo) {
        return new RequiredDocument(newDocumentNo, type, fileName, mediaType, sizeBytes, sha256, objectKey);
    }

    /** 中身の SHA-256 を 16 進で返す。 */
    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えません", e);
        }
    }
}
