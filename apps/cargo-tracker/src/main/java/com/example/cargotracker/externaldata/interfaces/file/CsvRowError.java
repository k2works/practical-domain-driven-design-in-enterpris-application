package com.example.cargotracker.externaldata.interfaces.file;

import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import java.util.Objects;

/**
 * 読めなかった CSV の行の誤り。例外にせず値で返し、ほかの行は読む（Bolt 39。原文と、ファイルの終わりまで飲み込んだ印は Bolt 39b2）。
 * 誤りの行がファイルの物理行のどこからどこまでかと、その原文を持つ。引用符が閉じないままファイルの終わりまで飲み込んだ誤り（引用符の閉じ忘れと、
 * 閉じた引用符の後の文字・引用符で囲まない値の中の引用符の行を読み飛ばす途中で飲み込んだもの。行 6b）は、終わりがファイルの最後の行になり、
 * 取込全体の要確認として取り込めなかった部分になる（理由は元のまま）。
 *
 * @param lineNumber 誤りの行が始まる物理行の番号（1 から）
 * @param lastLineNumber 誤りの行が終わる物理行の番号（引用符の中の改行で複数の物理行にまたがれば始まりより後）
 * @param defect 形式の誤り
 * @param raw 誤りの行の原文（始まりから終わりまで。最後の行末の改行を除き、引用符の中の改行は含む）
 * @param untilEndOfFile 引用符が閉じないままファイルの終わりまで飲み込んだか（引用符の閉じ忘れは常に真）
 */
public record CsvRowError(int lineNumber, int lastLineNumber, FormatDefect defect, String raw, boolean untilEndOfFile) {

    public CsvRowError {
        Objects.requireNonNull(defect, "defect");
        Objects.requireNonNull(raw, "raw");
        if (lastLineNumber < lineNumber) {
            throw new IllegalArgumentException("誤りの行の終わりが始まりより前: " + lineNumber + ".." + lastLineNumber);
        }
        if (defect == FormatDefect.UNCLOSED_QUOTE && !untilEndOfFile) {
            throw new IllegalArgumentException("引用符の閉じ忘れはファイルの終わりまでの誤り: " + lineNumber);
        }
    }
}
