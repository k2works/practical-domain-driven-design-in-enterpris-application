package com.example.cargotracker.externaldata.interfaces.file;

import java.util.Objects;

/**
 * 読めなかった CSV の行の誤り。例外にせず値で返し、ほかの行は読む（Bolt 39。隔離は 39b）。
 *
 * @param lineNumber 行が始まるファイルの行番号（1 から）
 * @param reason 理由
 */
public record CsvRowError(int lineNumber, Reason reason) {

    public CsvRowError {
        Objects.requireNonNull(reason, "reason");
    }

    /** 行の誤りの理由。 */
    public enum Reason {
        /** 引用符を閉じないままファイルが終わった。 */
        UNCLOSED_QUOTE,
        /** 閉じた引用符の後に、区切り（カンマか改行）でない文字がある。 */
        TEXT_AFTER_CLOSING_QUOTE,
        /** 引用符で囲まない値の中に引用符がある（RFC 4180 では引用符を含む値は引用符で囲む）。 */
        BARE_QUOTE,
        /** 列数がヘッダーと違う。 */
        COLUMN_COUNT_MISMATCH
    }
}
