package com.example.cargotracker.externaldata.interfaces.file;

import java.util.List;

/**
 * 取込ファイルの種別。種別ごとにヘッダーに必要な列が違う（Bolt 39 はキーの列、Bolt 39b2b で値の列を足した）。必要な列の順は、組み立てが
 * 列を検証する順でもある（キーの列 → 値の列）。航海番号は外部 ID の列の値なので、航海に航海番号の列はない。
 */
public enum CsvFileKind {
    /** 航海。寄港の列（{@code UN/LOCODE;到着;出発} を {@code |} で区切った並び）も要る。 */
    VOYAGE(List.of("external_id", "external_version", "acquired_at", "port_calls")),
    /** 主要実績。追跡番号・種類・場所・発生時刻の列も要る。 */
    MILESTONE(List.of(
            "external_id",
            "external_version",
            "acquired_at",
            "tracking_number",
            "kind",
            "location_unlocode",
            "occurred_at"));

    private final List<String> requiredColumns;

    CsvFileKind(List<String> requiredColumns) {
        this.requiredColumns = requiredColumns;
    }

    /** ヘッダーに必要な列の名前（検証の順）。 */
    public List<String> requiredColumns() {
        return List.copyOf(requiredColumns);
    }
}
