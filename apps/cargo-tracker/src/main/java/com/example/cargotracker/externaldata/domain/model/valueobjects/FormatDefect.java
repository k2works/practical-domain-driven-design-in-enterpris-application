package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.List;

/**
 * 形式の誤り。取込のファイルと行を隔離する理由（UC-15 2a。Bolt 39b2）。解析・ヘッダーの検証・組み立てがこの値を直接持ち、application に渡す。
 * 理由の名前は Bolt 39 で決めた名前を変えない。隔離の受信記録の注記を業務の言葉で作る（「&lt;物理行の範囲&gt;: &lt;理由&gt;。&lt;どうすればよいか&gt;」）。
 * 組み立ての検証の理由（空の値・時刻の書式・長すぎる値）は、それを使うステップで足す。
 */
@ValueObject
public enum FormatDefect {
    /** 文字コードが UTF-8 ではない（不正なバイト列。ファイルの誤り）。 */
    UNDECODABLE("文字コードが UTF-8 ではありません", "UTF-8 で保存し直したファイルを取り込んでください。"),
    /** ヘッダーの行を読めない（ファイルの誤り）。 */
    UNREADABLE_HEADER("ヘッダーを読めません", "ヘッダーの引用符を確かめてください。"),
    /** 必要な列がない（ヘッダーの行がない場合を含む。ファイルの誤り）。 */
    MISSING_COLUMN("必要な列がありません", ""),
    /** 同じ名前の列が 2 つ以上ある（ファイルの誤り）。 */
    DUPLICATE_COLUMN("同じ名前の列があります", ""),
    /** 引用符を閉じないままファイルが終わった（取込全体の要確認）。 */
    UNCLOSED_QUOTE("引用符が閉じていません", ""),
    /** 閉じた引用符の後に、区切り（カンマか改行）でない文字がある（行の誤り）。 */
    TEXT_AFTER_CLOSING_QUOTE("閉じた引用符の後に文字があります", ""),
    /** 引用符で囲まない値の中に引用符がある（RFC 4180 では引用符を含む値は引用符で囲む。行の誤り）。 */
    BARE_QUOTE("引用符で囲まない値の中に引用符があります", "引用符を含む値は引用符で囲んでください。"),
    /** 列数がヘッダーと違う（行の誤り）。 */
    COLUMN_COUNT_MISMATCH("列の数がヘッダーと違います", "");

    private final String reason;
    private final String remedy;

    FormatDefect(String reason, String remedy) {
        this.reason = reason;
        this.remedy = remedy;
    }

    /**
     * ファイルの誤りの注記（例「ファイル: 必要な列がありません（tracking_number）。」）。
     *
     * @param details 理由にかかわる列名など（括弧の中に書く）
     */
    public String fileNote(List<String> details) {
        return "ファイル: " + sentence(details);
    }

    /**
     * 行の誤りの注記（例「12 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。」）。
     *
     * @param lineNumber 行が始まる物理行の番号
     * @param lastLineNumber 行が終わる物理行の番号
     * @param details 理由にかかわる列名・列数など
     */
    public String rowNote(int lineNumber, int lastLineNumber, List<String> details) {
        String range =
                lineNumber == lastLineNumber ? lineNumber + " 行目" : lineNumber + " 行目から " + lastLineNumber + " 行目まで";
        return range + ": " + sentence(details);
    }

    /**
     * ファイルの終わりまで取り込めなかった範囲の注記（例「12 行目からファイルの終わりまで: 引用符が閉じていません。29 行を取り込めませんでした。
     * ファイルを確かめてください。」）。
     *
     * @param fromLine 範囲が始まる物理行の番号
     * @param lineCount 取り込めなかった物理行の数
     * @param details 理由にかかわる値
     */
    public String untilEndOfFileNote(int fromLine, int lineCount, List<String> details) {
        return fromLine + " 行目からファイルの終わりまで: " + sentence(details) + lineCount + " 行を取り込めませんでした。ファイルを確かめてください。";
    }

    private String sentence(List<String> details) {
        String detail = details.isEmpty() ? "" : "（" + String.join("、", details) + "）";
        return reason + detail + "。" + remedy;
    }
}
