package com.example.cargotracker.externaldata.application.internal.commands;

import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect.Extent;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues;
import com.example.cargotracker.shared.annotation.ddd.Command;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 取込のコマンド。情報源の 1 ファイルを取り込む入力（Bolt 39b。行の誤りと取り込めなかった部分は Bolt 39b2）。受け取った件数はファイルの
 * 物理データ行の数（ヘッダーとレコードの外の空行を除く）で、行の並びとは別に持つ。受け取った件数は、形式の正しい行と行の誤りの物理行の数の
 * 合計に取り込めなかった物理行の数を足したものに一致する（突き合わせは取込の結果。Bolt 39b2 の確認ポイント 9）。解析とキーの列・値の列の検証は
 * {@code interfaces.file} の組み立てが行い、このコマンドは値だけを受ける（不変条件は NULL と行番号だけ）。
 *
 * @param dataSourceId 情報源 ID
 * @param filePayload ファイル全体の原本のバイト列
 * @param receivedCount 受け取った件数
 * @param rows 形式の正しい行（物理行の順）
 * @param rejectedRows 行の誤り（物理行の順）
 * @param unreadable 取り込めなかった部分（ファイルの誤りか、引用符が閉じないままファイルの終わりまで飲み込んだ範囲。なければ {@code null}）
 */
@Command
public record IngestFileCommand(
        UUID dataSourceId,
        byte[] filePayload,
        int receivedCount,
        List<IngestedRow> rows,
        List<RejectedRow> rejectedRows,
        UnreadablePart unreadable) {

    /** 文字列の表現の原本の大きさの見出し（原本の中身は表現に出さない）。 */
    private static final String SIZE = ", size=";

    public IngestFileCommand {
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        filePayload = Objects.requireNonNull(filePayload, "filePayload").clone();
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        rejectedRows = List.copyOf(Objects.requireNonNull(rejectedRows, "rejectedRows"));
        if (receivedCount < 0) {
            throw new IllegalArgumentException("受け取った件数は 0 以上: " + receivedCount);
        }
    }

    @Override
    public byte[] filePayload() {
        return filePayload.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other
                        instanceof
                        IngestFileCommand(
                                UUID otherSource,
                                byte[] otherPayload,
                                int otherCount,
                                List<IngestedRow> otherRows,
                                List<RejectedRow> otherRejected,
                                UnreadablePart otherUnreadable)
                && dataSourceId.equals(otherSource)
                && Arrays.equals(filePayload, otherPayload)
                && receivedCount == otherCount
                && rows.equals(otherRows)
                && rejectedRows.equals(otherRejected)
                && Objects.equals(unreadable, otherUnreadable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceId, Arrays.hashCode(filePayload), receivedCount, rows, rejectedRows, unreadable);
    }

    @Override
    public String toString() {
        return "IngestFileCommand[dataSourceId=" + dataSourceId + SIZE + filePayload.length + ", receivedCount="
                + receivedCount + ", rows=" + rows.size() + ", rejectedRows=" + rejectedRows.size() + ", unreadable="
                + unreadable + "]";
    }

    /**
     * 取り込む行。取込ファイルの形式の正しい 1 行の値と原本。payload はその行の物理行の原文（最後の行末の改行を除き、引用符の中の改行は
     * 含む。UTF-8）。値の列から作った取込の値（航海の値か主要実績の値）を持つ（Bolt 39b2b）。取込のサービスは値を受信記録の列に書かず
     * （値は payload の行の原文に残る）、分類が採用の行の採用値の材料にする（Bolt 39b3）。コマンドの一部なので入れ子にする（単独のコマンドではない）。
     *
     * @param lineNumber 行が始まる物理行の番号
     * @param lastLineNumber 行が終わる物理行の番号（引用符の中の改行で複数の物理行にまたがれば始まりより後。Bolt 39b2）
     * @param externalId 外部 ID
     * @param externalVersion 外部版
     * @param acquiredAt 取得時刻
     * @param values 取込の値
     * @param payload 行の原本のバイト列
     */
    public record IngestedRow(
            int lineNumber,
            int lastLineNumber,
            String externalId,
            String externalVersion,
            Instant acquiredAt,
            RecordValues values,
            byte[] payload) {

        public IngestedRow {
            Objects.requireNonNull(externalId, "externalId");
            Objects.requireNonNull(externalVersion, "externalVersion");
            Objects.requireNonNull(acquiredAt, "acquiredAt");
            Objects.requireNonNull(values, "values");
            payload = Objects.requireNonNull(payload, "payload").clone();
            requireLineRange(lineNumber, lastLineNumber);
        }

        /** 行の物理行の範囲。 */
        public PhysicalLineRange range() {
            return new PhysicalLineRange(lineNumber, lastLineNumber);
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other
                            instanceof
                            IngestedRow(
                                    int otherLine,
                                    int otherLast,
                                    String otherId,
                                    String otherVersion,
                                    Instant otherAcquired,
                                    RecordValues otherValues,
                                    byte[] otherPayload)
                    && lineNumber == otherLine
                    && lastLineNumber == otherLast
                    && externalId.equals(otherId)
                    && externalVersion.equals(otherVersion)
                    && acquiredAt.equals(otherAcquired)
                    && values.equals(otherValues)
                    && Arrays.equals(payload, otherPayload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    lineNumber,
                    lastLineNumber,
                    externalId,
                    externalVersion,
                    acquiredAt,
                    values,
                    Arrays.hashCode(payload));
        }

        @Override
        public String toString() {
            return "IngestedRow[lineNumber=" + lineNumber + ", lastLineNumber=" + lastLineNumber + ", externalId="
                    + externalId + ", externalVersion=" + externalVersion + ", acquiredAt=" + acquiredAt + ", values="
                    + values + SIZE + payload.length + "]";
        }
    }

    /**
     * 行の誤り。形式の誤りで外部原本を持てない 1 行（Bolt 39b2）。payload はその行の物理行の原文（形式の正しい行と同じ範囲。UTF-8）で、
     * 取込のサービスが保存してからキーのない隔離の受信記録にする。理由は行の誤りになりうるものに限る（{@link Extent#ROW}）。
     *
     * @param lineNumber 行が始まる物理行の番号
     * @param lastLineNumber 行が終わる物理行の番号
     * @param defect 形式の誤り
     * @param details 注記の詳細（理由にかかわる列名・列数など。注記の括弧の中に書く）
     * @param payload 行の原文のバイト列
     */
    public record RejectedRow(
            int lineNumber, int lastLineNumber, FormatDefect defect, List<String> details, byte[] payload) {

        public RejectedRow {
            Objects.requireNonNull(defect, "defect");
            details = List.copyOf(Objects.requireNonNull(details, "details"));
            payload = Objects.requireNonNull(payload, "payload").clone();
            requireLineRange(lineNumber, lastLineNumber);
            if (!defect.occursIn(Extent.ROW)) {
                throw new IllegalArgumentException("行の誤りにならない理由: " + defect);
            }
        }

        /** 行の物理行の範囲。 */
        public PhysicalLineRange range() {
            return new PhysicalLineRange(lineNumber, lastLineNumber);
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other
                            instanceof
                            RejectedRow(
                                    int otherLine,
                                    int otherLast,
                                    FormatDefect otherDefect,
                                    List<String> otherDetails,
                                    byte[] otherPayload)
                    && lineNumber == otherLine
                    && lastLineNumber == otherLast
                    && defect == otherDefect
                    && details.equals(otherDetails)
                    && Arrays.equals(payload, otherPayload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lineNumber, lastLineNumber, defect, details, Arrays.hashCode(payload));
        }

        @Override
        public String toString() {
            return "RejectedRow[lineNumber=" + lineNumber + ", lastLineNumber=" + lastLineNumber + ", defect=" + defect
                    + ", details=" + details + SIZE + payload.length + "]";
        }
    }

    /**
     * 取り込めなかった部分（Bolt 39b2）。ファイルの誤り（文字コード・ヘッダーが読めない・必要な列がない・同じ名前の列）は原文を持たず
     * （{@code payload} が {@code null}）、取込のサービスはファイル全体の原本を指す。データ行があれば始まりは最初のデータ行、数は受け取った
     * 件数のすべてで、データ行がなければ始まりは {@code null}、数は 0。引用符が閉じないままファイルの終わりまで飲み込んだ範囲は、始まりの行から
     * ファイルの終わりまでの原文を持つ。どちらも取込全体の要確認になる。ファイルの誤りかどうかは理由の範囲（{@link Extent}）で決め、理由と
     * 原文の有無と物理行の組み合わせをコンストラクタで確かめる（原文の有無でファイルの誤りを判定しない。Bolt 39b2 の開発レビュー）。
     *
     * @param fromLine 取り込めなかった行の始まり（物理行の番号。数が 0 なら {@code null}）
     * @param lineCount 取り込めなかった物理行の数
     * @param defect 形式の誤り
     * @param details 注記の詳細（足りない列の名前など）
     * @param payload 範囲の原文のバイト列（ファイルの誤りは {@code null}）
     */
    public record UnreadablePart(
            Integer fromLine, int lineCount, FormatDefect defect, List<String> details, byte[] payload) {

        public UnreadablePart {
            Objects.requireNonNull(defect, "defect");
            details = List.copyOf(Objects.requireNonNull(details, "details"));
            payload = payload == null ? null : payload.clone();
            boolean none = lineCount == 0 && fromLine == null;
            boolean some = lineCount > 0 && fromLine != null && fromLine >= 1;
            if (!none && !some) {
                throw new IllegalArgumentException("取り込めなかった行の始まりと数が合わない: " + fromLine + "・" + lineCount);
            }
            requireExtent(defect, payload != null, none);
        }

        /** 理由の範囲と、原文の有無・物理行の有無の組み合わせを確かめる。 */
        private static void requireExtent(FormatDefect defect, boolean hasPayload, boolean noLines) {
            if (defect.occursIn(Extent.FILE)) {
                if (hasPayload) {
                    throw new IllegalArgumentException("ファイルの誤りは原文を持たずファイル全体の原本を指す: " + defect);
                }
            } else if (!defect.occursIn(Extent.UNTIL_END_OF_FILE)) {
                throw new IllegalArgumentException("取り込めなかった部分にならない理由: " + defect);
            } else if (!hasPayload || noLines) {
                throw new IllegalArgumentException("ファイルの終わりまでの範囲は物理行と原文を持つ: " + defect);
            }
        }

        @Override
        public byte[] payload() {
            return payload == null ? null : payload.clone();
        }

        /** ファイルの誤りか（理由の範囲がファイル。原文を持たず、ファイル全体の原本を指す）。 */
        public boolean fileDefect() {
            return defect.occursIn(Extent.FILE);
        }

        @Override
        public boolean equals(Object other) {
            return other
                            instanceof
                            UnreadablePart(
                                    Integer otherFrom,
                                    int otherCount,
                                    FormatDefect otherDefect,
                                    List<String> otherDetails,
                                    byte[] otherPayload)
                    && Objects.equals(fromLine, otherFrom)
                    && lineCount == otherCount
                    && defect == otherDefect
                    && details.equals(otherDetails)
                    && Arrays.equals(payload, otherPayload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fromLine, lineCount, defect, details, Arrays.hashCode(payload));
        }

        @Override
        public String toString() {
            return "UnreadablePart[fromLine=" + fromLine + ", lineCount=" + lineCount + ", defect=" + defect
                    + ", details=" + details + SIZE + (payload == null ? "-" : payload.length) + "]";
        }
    }

    private static void requireLineRange(int lineNumber, int lastLineNumber) {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("物理行の番号は 1 以上: " + lineNumber);
        }
        if (lastLineNumber < lineNumber) {
            throw new IllegalArgumentException("行の終わりが始まりより前: " + lineNumber + ".." + lastLineNumber);
        }
    }
}
