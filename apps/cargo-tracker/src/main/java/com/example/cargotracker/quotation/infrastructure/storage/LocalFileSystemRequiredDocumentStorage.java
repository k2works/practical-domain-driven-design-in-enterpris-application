package com.example.cargotracker.quotation.infrastructure.storage;

import com.example.cargotracker.quotation.domain.model.aggregates.RequiredDocumentStorage;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 書類の保存のローカルのファイルシステムの実装（開発環境。ADR-007）。本番の S3 の実装は運用準備（W10、#28）で作る。
 * オブジェクトキーはファイル名を使わず {@code quotation/{輸送要求 ID}/{UUID}} にし、形の違うキーは読まない（保存先の外を指させない）。
 */
public class LocalFileSystemRequiredDocumentStorage implements RequiredDocumentStorage {

    private static final String UUID_TEXT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern OBJECT_KEY = Pattern.compile("quotation/" + UUID_TEXT + "/" + UUID_TEXT);

    private final Path baseDir;

    public LocalFileSystemRequiredDocumentStorage(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
    }

    @Override
    public String store(TransportRequestId transportRequestId, byte[] content) {
        String objectKey = "quotation/" + transportRequestId.value() + "/" + UUID.randomUUID();
        Path file = resolve(objectKey);
        try {
            // オブジェクトキーは quotation/{ID}/{UUID} の形なので、親のディレクトリは必ずある
            Files.createDirectories(Objects.requireNonNull(file.getParent(), "parent"));
            // 同じキーを上書きしない（新しい版の書類も新しいキーにする）
            Files.write(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("書類を保存できません: " + objectKey, e);
        }
        return objectKey;
    }

    @Override
    public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (IOException e) {
            throw new UncheckedIOException("書類を読めません: " + objectKey, e);
        }
    }

    private Path resolve(String objectKey) {
        if (!OBJECT_KEY.matcher(objectKey).matches()) {
            throw new IllegalArgumentException("書類のオブジェクトキーの形が違います: " + objectKey);
        }
        Path file = baseDir.resolve(objectKey).normalize();
        if (!file.startsWith(baseDir)) {
            throw new IllegalArgumentException("保存先の外を指しています: " + objectKey);
        }
        return file;
    }
}
