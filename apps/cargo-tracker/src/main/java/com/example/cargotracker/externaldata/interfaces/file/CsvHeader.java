package com.example.cargotracker.externaldata.interfaces.file;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV のヘッダーの検証。列名から位置への写しか、ファイルの誤り（形式の誤りの理由）を値で返す（Bolt 39。原本の保存と隔離は 39b）。
 */
public sealed interface CsvHeader {

    /** 読んだ CSV のヘッダーを種別に必要な列で検証する。ヘッダーの行を読めなければファイルの誤りにする。 */
    static CsvHeader check(CsvRecords records, CsvFileKind kind) {
        if (records.headerUnreadable()) {
            return new FileError(Reason.UNREADABLE_HEADER, List.of());
        }
        return check(records.header(), kind);
    }

    /** ヘッダーの列名を種別に必要な列で検証する。 */
    static CsvHeader check(List<String> columns, CsvFileKind kind) {
        Map<String, Integer> positions = new LinkedHashMap<>();
        List<String> duplicated = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            String name = columns.get(i);
            if (positions.putIfAbsent(name, i) != null && !duplicated.contains(name)) {
                duplicated.add(name);
            }
        }
        if (!duplicated.isEmpty()) {
            return new FileError(Reason.DUPLICATE_COLUMN, duplicated);
        }
        List<String> missing = kind.requiredColumns().stream()
                .filter(required -> !positions.containsKey(required))
                .toList();
        if (!missing.isEmpty()) {
            return new FileError(Reason.MISSING_COLUMN, missing);
        }
        return new Columns(positions);
    }

    /**
     * 必要な列がそろったヘッダー。
     *
     * @param positions 列名から位置（0 から）への写し
     */
    record Columns(Map<String, Integer> positions) implements CsvHeader {

        public Columns {
            positions = Map.copyOf(positions);
        }
    }

    /**
     * ファイルの誤り。
     *
     * @param reason 理由
     * @param columns 理由にかかわる列の名前（足りない列、重なった列）
     */
    record FileError(Reason reason, List<String> columns) implements CsvHeader {

        public FileError {
            columns = List.copyOf(columns);
        }
    }

    /** ファイルの誤りの理由。 */
    enum Reason {
        /** 必要な列がない（ヘッダーの行がない場合を含む）。 */
        MISSING_COLUMN,
        /** 同じ名前の列が 2 つ以上ある。 */
        DUPLICATE_COLUMN,
        /** ヘッダーの行を読めない（行の誤りがある。誤りは {@link CsvRecords#errors()} にある）。 */
        UNREADABLE_HEADER
    }
}
