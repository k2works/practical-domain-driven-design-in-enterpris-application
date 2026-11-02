package com.example.cargotracker.externaldata.interfaces.file;

import java.util.Objects;

/**
 * 読めなかった CSV の行の誤り。例外にせず値で返し、ほかの行は読む（Bolt 39。隔離は 39b）。誤りの行がファイルの物理行のどこからどこまでかを持つ。
 * 引用符の閉じ忘れはファイルの終わりまでを飲み込むので、終わりはファイルの最後の行になる（39b で取込全体の要確認として扱い、取り込めなかった件数を示す）。
 *
 * @param lineNumber 誤りの行が始まる物理行の番号（1 から）
 * @param lastLineNumber 誤りの行が終わる物理行の番号（引用符の中の改行で複数の物理行にまたがれば始まりより後）
 * @param reason 理由
 */
public record CsvRowError(int lineNumber, int lastLineNumber, Reason reason) {

    public CsvRowError {
        Objects.requireNonNull(reason, "reason");
        if (lastLineNumber < lineNumber) {
            throw new IllegalArgumentException("誤りの行の終わりが始まりより前: " + lineNumber + ".." + lastLineNumber);
        }
    }

    /** 1 つの物理行に収まる誤り。 */
    public CsvRowError(int lineNumber, Reason reason) {
        this(lineNumber, lineNumber, reason);
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
