package com.example.cargotracker.quotation.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ローカルのファイルシステムの書類の保存（開発環境。ADR-007）。
 */
class LocalFileSystemRequiredDocumentStorageTest {

    @TempDir
    Path baseDir;

    private final TransportRequestId id = new TransportRequestId(UUID.randomUUID());

    @Test
    void 保存するとファイル名を使わないキーを返しキーで同じ中身を読める() {
        LocalFileSystemRequiredDocumentStorage storage = new LocalFileSystemRequiredDocumentStorage(baseDir);
        byte[] content = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);

        String objectKey = storage.store(id, content);

        assertThat(objectKey).matches("quotation/" + id.value() + "/[0-9a-f-]{36}");
        assertThat(storage.read(objectKey)).isEqualTo(content);
        assertThat(baseDir.resolve(objectKey)).exists();
    }

    @Test
    void 同じ中身を2回保存しても別のキーになる() {
        LocalFileSystemRequiredDocumentStorage storage = new LocalFileSystemRequiredDocumentStorage(baseDir);
        byte[] content = {1, 2, 3};

        assertThat(storage.store(id, content)).isNotEqualTo(storage.store(id, content));
    }

    @Test
    void 形の違うキーと保存先の外を指すキーは読まない() {
        LocalFileSystemRequiredDocumentStorage storage = new LocalFileSystemRequiredDocumentStorage(baseDir);

        assertThatThrownBy(() -> storage.read("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        String outside = "quotation/" + id.value() + "/../../../secret";
        assertThatThrownBy(() -> storage.read(outside)).isInstanceOf(IllegalArgumentException.class);
    }
}
