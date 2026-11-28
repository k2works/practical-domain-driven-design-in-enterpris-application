package com.example.cargotracker.externaldata.interfaces.file;

import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect.Extent;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import java.util.Objects;

/**
 * 読めなかった CSV の行の誤り。例外にせず値で返し、ほかの行は読む（Bolt 39。原文と、ファイルの終わりまで飲み込んだ印と列数は Bolt 39b2）。
 * 誤りの行がファイルの物理行のどこからどこまでかと、その原文を持つ。引用符が閉じないままファイルの終わりまで飲み込んだ誤り（引用符の閉じ忘れと、
 * 閉じた引用符の後の文字・引用符で囲まない値の中の引用符の行を読み飛ばす途中で飲み込んだもの。行 6b）は、終わりがファイルの最後の行になり、
 * 取込全体の要確認として取り込めなかった部分になる（理由は元のまま）。理由は行かファイルの終わりまでの範囲になりうるものに限る
 * （{@link FormatDefect#occursIn(Extent)}）。
 *
 * @param lineNumber 誤りの行が始まる物理行の番号（1 から）
 * @param lastLineNumber 誤りの行が終わる物理行の番号（引用符の中の改行で複数の物理行にまたがれば始まりより後）
 * @param defect 形式の誤り
 * @param raw 誤りの行の原文（始まりから終わりまで。最後の行末の改行を除き、引用符の中の改行は含む）
 * @param untilEndOfFile 引用符が閉じないままファイルの終わりまで飲み込んだか（引用符の閉じ忘れは常に真）
 * @param columnCount 列数の違う行の、解析で読んだ値の数（列数の違いのときだけ持ち、ほかは {@code null}。注記の詳細に使う）
 */
public record CsvRowError(
        int lineNumber,
        int lastLineNumber,
        FormatDefect defect,
        String raw,
        boolean untilEndOfFile,
        Integer columnCount) {

    public CsvRowError {
        Objects.requireNonNull(defect, "defect");
        Objects.requireNonNull(raw, "raw");
        if (lastLineNumber < lineNumber) {
            throw new IllegalArgumentException("誤りの行の終わりが始まりより前: " + lineNumber + ".." + lastLineNumber);
        }
        if (!defect.occursIn(untilEndOfFile ? Extent.UNTIL_END_OF_FILE : Extent.ROW)) {
            throw new IllegalArgumentException(
                    "読めなかった行の誤りにならない理由: " + defect + (untilEndOfFile ? "（ファイルの終わりまで）" : "（行）"));
        }
        if ((defect == FormatDefect.COLUMN_COUNT_MISMATCH) != (columnCount != null)) {
            throw new IllegalArgumentException("列数は列数の違う行の誤りだけが持つ: " + defect + "・" + columnCount);
        }
    }

    /** 列数を持たない誤り（列数の違いのほか）。 */
    public CsvRowError(int lineNumber, int lastLineNumber, FormatDefect defect, String raw, boolean untilEndOfFile) {
        this(lineNumber, lastLineNumber, defect, raw, untilEndOfFile, null);
    }

    /** 誤りの物理行の範囲。 */
    public PhysicalLineRange range() {
        return new PhysicalLineRange(lineNumber, lastLineNumber);
    }
}
