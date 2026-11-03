package com.example.cargotracker.externaldata.interfaces.file;

import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV のヘッダーの検証。列名から位置への写しか、ファイルの誤り（{@link FormatDefect}）を値で返す（Bolt 39。文字コードの誤りは Bolt 39b2。
 * ファイルの誤りの原本の保存とキーのない隔離は Bolt 39b2 の取込のサービス）。
 */
public sealed interface CsvHeader {

    /** 読んだ CSV のヘッダーを種別に必要な列で検証する。文字コードの誤りか、ヘッダーの行を読めなければファイルの誤りにする。 */
    static CsvHeader check(CsvRecords records, CsvFileKind kind) {
        if (records.undecodable()) {
            return new FileError(FormatDefect.UNDECODABLE, List.of());
        }
        if (records.headerUnreadable()) {
            return new FileError(FormatDefect.UNREADABLE_HEADER, List.of());
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
            return new FileError(FormatDefect.DUPLICATE_COLUMN, duplicated);
        }
        List<String> missing = kind.requiredColumns().stream()
                .filter(required -> !positions.containsKey(required))
                .toList();
        if (!missing.isEmpty()) {
            return new FileError(FormatDefect.MISSING_COLUMN, missing);
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
     * @param defect 形式の誤り
     * @param columns 理由にかかわる列の名前（足りない列、重なった列）
     */
    record FileError(FormatDefect defect, List<String> columns) implements CsvHeader {

        public FileError {
            columns = List.copyOf(columns);
        }
    }
}
