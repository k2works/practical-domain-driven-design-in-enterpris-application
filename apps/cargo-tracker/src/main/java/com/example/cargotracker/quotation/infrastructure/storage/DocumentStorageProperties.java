package com.example.cargotracker.quotation.infrastructure.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 書類の保存先の設定（{@code cargotracker.document-storage.base-dir}）。指定がなければ一時ディレクトリの下に置く。
 *
 * @param baseDir 保存先のディレクトリ
 */
@ConfigurationProperties("cargotracker.document-storage")
public record DocumentStorageProperties(Path baseDir) {

    public DocumentStorageProperties {
        if (baseDir == null) {
            baseDir = Path.of(System.getProperty("java.io.tmpdir"), "cargo-tracker", "documents");
        }
    }
}
