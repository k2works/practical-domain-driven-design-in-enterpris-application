package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.queryservices.DocumentFile;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 * 必要書類の画面の部品（C-03 の添付、C-04 と S-03 の書類の一覧と取得。Bolt 7）。荷主と営業の画面で共通に使う。
 */
public final class RequiredDocumentViews {

    /** ファイル名の長さの上限（データモデル `file_name`）。 */
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private static final String UNNAMED = "document";

    private RequiredDocumentViews() {}

    /** フォームで選んだファイルを添付にする。選んでいない欄（空のファイル名）は無視する。 */
    static List<RequiredDocumentAttachment> attachments(TransportRequestForm form) {
        List<RequiredDocumentAttachment> attachments = new ArrayList<>();
        add(attachments, DocumentType.COMMERCIAL_INVOICE, form.getCommercialInvoice());
        add(attachments, DocumentType.PACKING_LIST, form.getPackingList());
        form.getOtherDocuments().forEach(file -> add(attachments, DocumentType.OTHER, file));
        return attachments;
    }

    /**
     * ファイルを 1 件でも選んで送ったか。誤りで画面を出し直すとブラウザはファイルの選択を残せないため、
     * 選んでいたときは選び直しを案内する（Bolt 6〜8 レビュー R-02）。
     */
    static boolean hasSelectedFiles(TransportRequestForm form) {
        return Stream.concat(
                        Stream.of(form.getCommercialInvoice(), form.getPackingList()),
                        form.getOtherDocuments().stream())
                .anyMatch(file -> file != null
                        && file.getOriginalFilename() != null
                        && !file.getOriginalFilename().isBlank());
    }

    private static void add(List<RequiredDocumentAttachment> attachments, DocumentType type, MultipartFile file) {
        String original = file == null ? null : file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return;
        }
        try {
            attachments.add(new RequiredDocumentAttachment(type, fileName(original), file.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("添付したファイルを読めません", e);
        }
    }

    /** 画面に出すファイル名。パスの部分と制御文字を除き、255 文字までにする（保存のキーには使わない）。 */
    static String fileName(String original) {
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "")
                .strip();
        if (name.isEmpty()) {
            return UNNAMED;
        }
        return name.codePointCount(0, name.length()) <= MAX_FILE_NAME_LENGTH
                ? name
                : name.substring(0, name.offsetByCodePoints(0, MAX_FILE_NAME_LENGTH));
    }

    /** 現在の版の書類の一覧の行。取得の URL は画面の領域（荷主・社内）ごとに渡す。 */
    static List<Row> rows(TransportRequest request, String basePath) {
        int versionNo = request.currentVersion().versionNo();
        return request.currentVersion().terms().documents().stream()
                .map(document -> new Row(
                        type(document.type()),
                        document.fileName(),
                        size(document.sizeBytes()),
                        basePath + "/" + request.number().text() + "/versions/" + versionNo + "/documents/"
                                + document.documentNo()))
                .toList();
    }

    /**
     * 書類をダウンロードさせる応答。ブラウザの中で開かせない（{@code attachment}、{@code nosniff}。2026-10-03 に承認）。
     * ファイル名は RFC 6266 の {@code filename*} で返す。
     */
    static ResponseEntity<byte[]> download(DocumentFile file) {
        RequiredDocument document = file.document();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.mediaType().contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(document.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }

    static String type(DocumentType type) {
        return switch (type) {
            case COMMERCIAL_INVOICE -> "商業送り状";
            case PACKING_LIST -> "梱包明細";
            case OTHER -> "その他";
        };
    }

    /** 大きさの表示（例: 120 KB、1.5 MB）。KB は切り上げる。 */
    static String size(long bytes) {
        if (bytes < 1024L * 1024) {
            return ((bytes + 1023) / 1024) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }

    /**
     * 書類の一覧の 1 行。
     *
     * @param type 書類の種類
     * @param fileName ファイル名
     * @param size 大きさ
     * @param url 取得の URL
     */
    public record Row(String type, String fileName, String size, String url) {}
}
