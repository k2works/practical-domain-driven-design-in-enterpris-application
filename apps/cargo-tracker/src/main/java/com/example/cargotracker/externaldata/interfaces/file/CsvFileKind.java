package com.example.cargotracker.externaldata.interfaces.file;

import java.util.List;

/**
 * 取込ファイルの種別。種別ごとにヘッダーに必要な列が違う（Bolt 39。application に渡す値の組の型は 39b で決める）。
 */
public enum CsvFileKind {
    /** 航海。 */
    VOYAGE(List.of("external_id", "external_version", "acquired_at")),
    /** 主要実績。追跡番号の列も要る。 */
    MILESTONE(List.of("external_id", "external_version", "acquired_at", "tracking_number"));

    private final List<String> requiredColumns;

    CsvFileKind(List<String> requiredColumns) {
        this.requiredColumns = requiredColumns;
    }

    /** ヘッダーに必要な列の名前。 */
    public List<String> requiredColumns() {
        return List.copyOf(requiredColumns);
    }
}
