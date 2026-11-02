package com.example.cargotracker.externaldata.interfaces.file;

import java.util.List;

/**
 * CSV の 1 行（1 レコード）の値の並び（Bolt 39）。
 *
 * @param lineNumber 行が始まるファイルの行番号（1 から。引用符の中の改行も数える）
 * @param values 値の並び（引用符を外し、{@code ""} を {@code "} にしたもの）
 */
public record CsvRow(int lineNumber, List<String> values) {

    public CsvRow {
        values = List.copyOf(values);
    }
}
