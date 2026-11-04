package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 形式の誤り。取込のファイルと行を隔離する理由（UC-15 2a。Bolt 39b2）。解析・ヘッダーの検証・組み立てがこの値を直接持ち、application に渡す。
 * 理由の名前は Bolt 39 で決めた名前を変えない。隔離の受信記録の注記を業務の言葉で作る（「&lt;物理行の範囲&gt;: &lt;理由&gt;。&lt;どうすればよいか&gt;」）。
 * 空の値・時刻の書式・長すぎる値は組み立て（{@code interfaces.file}）がキーの列と値の列を検証して見つける。場所・種類・追跡番号・
 * 寄港の列の形の誤りは、組み立てが値の列から取込の値を作るときの拒否を写す（Bolt 39b2b）。
 *
 * <p>理由ごとに、なりうる範囲（{@link Extent}。ファイル・行・ファイルの終わりまで）を持つ。行の誤り・取り込めなかった部分・ヘッダーの
 * ファイルの誤りは、組み合わせをこの範囲で確かめる（原文の有無で判定しない。Bolt 39b2 の開発レビュー）。
 */
@ValueObject
public enum FormatDefect {
    /** 文字コードが UTF-8 ではない（不正なバイト列。ファイルの誤り）。 */
    UNDECODABLE("文字コードが UTF-8 ではありません", "UTF-8 で保存し直したファイルを取り込んでください。", Extent.FILE),
    /** ヘッダーの行を読めない（ファイルの誤り）。 */
    UNREADABLE_HEADER("ヘッダーを読めません", "ヘッダーの引用符を確かめてください。", Extent.FILE),
    /**
     * 必要な列がない（ヘッダーの行がない場合と、選んだ情報源と違う種別のファイルを含む。ファイルの誤り）。どうすればよいかは種別の確かめを先に
     * する（Bolt 39c3 の開始準備の決定 (8)）。
     */
    MISSING_COLUMN("必要な列がありません", "選んだ情報源の種別に合ったファイルか確かめてください。合っていれば" + Remedy.ASK_PROVIDER, Extent.FILE),
    /** 同じ名前の列が 2 つ以上ある（ファイルの誤り。どうすればよいかは Bolt 39c3）。 */
    DUPLICATE_COLUMN("同じ名前の列があります", Remedy.ASK_PROVIDER, Extent.FILE),
    /** 引用符を閉じないままファイルが終わった（取込全体の要確認）。 */
    UNCLOSED_QUOTE("引用符が閉じていません", "", Extent.UNTIL_END_OF_FILE),
    /** 閉じた引用符の後に、区切り（カンマか改行）でない文字がある（行の誤り。読み飛ばす途中で飲み込めばファイルの終わりまで）。 */
    TEXT_AFTER_CLOSING_QUOTE("閉じた引用符の後に文字があります", Remedy.ASK_PROVIDER, Extent.ROW, Extent.UNTIL_END_OF_FILE),
    /**
     * 引用符で囲まない値の中に引用符がある（RFC 4180 では引用符を含む値は引用符で囲む。行の誤り。読み飛ばす途中で飲み込めばファイルの
     * 終わりまで）。
     */
    BARE_QUOTE("引用符で囲まない値の中に引用符があります", "引用符を含む値は引用符で囲んでください。", Extent.ROW, Extent.UNTIL_END_OF_FILE),
    /** 列数がヘッダーと違う（行の誤り）。 */
    COLUMN_COUNT_MISMATCH("列の数がヘッダーと違います", Remedy.ASK_PROVIDER, Extent.ROW),
    /** キーの列か値の列の値が空か空白だけ（値は加工せずに確かめる。行の誤り）。 */
    EMPTY_VALUE("値が空です", Remedy.ASK_PROVIDER, Extent.ROW),
    /** 時刻がオフセット付きの ISO 8601 でない（オフセットのない値と、日付として誤りの値を含む。行の誤り）。 */
    INVALID_TIME("時刻の書式が違います", "オフセット付きの ISO 8601（例 2026-11-03T09:00:00+09:00）で書いてください。", Extent.ROW),
    /** 値が表の列の上限の文字数より長い（行の誤り）。 */
    TOO_LONG("値が長すぎます", Remedy.ASK_PROVIDER, Extent.ROW),
    /** 場所（寄港の港を含む）が UN/LOCODE の形でない（値は加工しない。行の誤り。Bolt 39b2b）。 */
    INVALID_LOCATION("場所の形が違います", "UN/LOCODE（国コード 2 文字 + 地点コード 3 文字。例 JPTYO）で書いてください。", Extent.ROW),
    /** 実績の種類が 6 つの種類のどれでもない（小文字・前後の空白を含む。行の誤り。Bolt 39b2b）。 */
    INVALID_KIND("種類が違います", Remedy.ONE_OF_MILESTONE_KINDS, Extent.ROW),
    /** 追跡番号が追跡番号の形でない（行の誤り。Bolt 39b2b）。 */
    INVALID_TRACKING_NUMBER("追跡番号の形が違います", "CT と英大文字・数字 12 桁（0・O・1・I・L を除く）で書いてください。", Extent.ROW),
    /**
     * 寄港の列の形が違う（寄港の区切り、寄港の数（{@link VoyageValues#MIN_PORT_CALLS} 以上）、到着か出発の有無、到着と出発の順、寄港の重複。
     * 原因と寄港の番号は詳細に書き（{@link VoyageValuesRejected#detail()}）、どうすればよいかは短い業務の言葉にする。行の誤り。Bolt 39b2b）。
     */
    INVALID_PORT_CALLS("寄港の列の形が違います", "寄港の並びを提供元に確かめてください。", Extent.ROW);

    /** どうすればよいかの言葉で、理由をまたいで使うもの。 */
    private static final class Remedy {

        /** 行の誤りとファイルの誤りで、ファイルの中から直し方を示せないとき。 */
        static final String ASK_PROVIDER = "提供元に確かめてください。";

        /** 実績の種類の誤りで、6 つの種類の名前を示す（種類の値から作り、名前を重ねて書かない）。 */
        static final String ONE_OF_MILESTONE_KINDS =
                Arrays.stream(MilestoneKindValue.values()).map(Enum::name).collect(Collectors.joining("・"))
                        + " のどれかで書いてください。";

        private Remedy() {}
    }

    /** 形式の誤りの範囲。 */
    public enum Extent {
        /** ファイル全体（行を読まない。ファイル全体の原本を指す）。 */
        FILE,
        /** 1 行（その行の原文を指す）。 */
        ROW,
        /** その行からファイルの終わりまで（引用符が閉じないまま飲み込んだ。範囲の原文を指し、取込全体の要確認）。 */
        UNTIL_END_OF_FILE
    }

    private final String reason;
    private final String remedy;
    private final Set<Extent> extents;

    FormatDefect(String reason, String remedy, Extent... extents) {
        this.reason = reason;
        this.remedy = remedy;
        this.extents = Set.of(extents);
    }

    /** この理由がその範囲の誤りになりうるか。 */
    public boolean occursIn(Extent extent) {
        return extents.contains(extent);
    }

    /**
     * ファイルの誤りの注記（例「ファイル: 同じ名前の列があります（external_id）。提供元に確かめてください。」）。範囲がファイルの理由は、
     * すべてどうすればよいかを持つ（Bolt 39c3）。
     *
     * @param details 理由にかかわる列名など（括弧の中に書く）
     */
    public String fileNote(List<String> details) {
        return "ファイル: " + sentence(details);
    }

    /**
     * 行の誤りの注記（例「12 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。提供元に確かめてください。」）。
     *
     * @param range 行の物理行の範囲
     * @param details 理由にかかわる列名・列数など
     */
    public String rowNote(PhysicalLineRange range, List<String> details) {
        return range.wording() + ": " + sentence(details);
    }

    /**
     * ファイルの終わりまで取り込めなかった範囲の注記（例「12 行目からファイルの終わりまで: 引用符が閉じていません。29 行を取り込めませんでした。
     * ファイルを確かめてください。」）。どうすればよいかは範囲の言葉（ファイルを確かめる）にそろえ、行の誤りのときの言葉を重ねない。
     *
     * @param fromLine 範囲が始まる物理行の番号
     * @param lineCount 取り込めなかった物理行の数
     * @param details 理由にかかわる値
     */
    public String untilEndOfFileNote(int fromLine, int lineCount, List<String> details) {
        return fromLine + " 行目からファイルの終わりまで: " + reasonOf(details) + lineCount + " 行を取り込めませんでした。ファイルを確かめてください。";
    }

    private String sentence(List<String> details) {
        return reasonOf(details) + remedy;
    }

    private String reasonOf(List<String> details) {
        String detail = details.isEmpty() ? "" : "（" + String.join("、", details) + "）";
        return reason + detail + "。";
    }
}
