package com.example.cargotracker.externaldata.interfaces.file;

import java.util.List;
import java.util.Objects;

/**
 * CSV の 1 行（1 レコード）の値の並びと原文（Bolt 39。終わりの物理行と原文は Bolt 39b2）。
 *
 * @param lineNumber 行が始まるファイルの物理行の番号（1 から。引用符の中の改行も数える）
 * @param lastLineNumber 行が終わる物理行の番号（引用符の中の改行で複数の物理行にまたがれば始まりより後）
 * @param values 値の並び（引用符を外し、{@code ""} を {@code "} にしたもの）
 * @param raw 行の原文（始まりから終わりまで。最後の行末の改行を除き、引用符の中の改行は含む。BOM を含めない）
 */
public record CsvRow(int lineNumber, int lastLineNumber, List<String> values, String raw) {

    public CsvRow {
        values = List.copyOf(values);
        Objects.requireNonNull(raw, "raw");
        if (lastLineNumber < lineNumber) {
            throw new IllegalArgumentException("行の終わりが始まりより前: " + lineNumber + ".." + lastLineNumber);
        }
    }
}
