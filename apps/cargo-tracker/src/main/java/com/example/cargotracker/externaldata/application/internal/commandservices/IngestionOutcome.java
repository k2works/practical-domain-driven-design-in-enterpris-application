package com.example.cargotracker.externaldata.application.internal.commandservices;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 取込の結果（{@link IngestionService#ingest}。Bolt 39b。取り込めなかった部分と件数の突き合わせは Bolt 39b2）。拒否のときは原本も含めて
 * 何も保存しない（例外にせず種類で返す）。
 */
public sealed interface IngestionOutcome {

    /**
     * 取り込んだ。
     *
     * @param ingestionId 取込 ID
     * @param receivedCount 受け取った件数（ファイルの物理データ行の数。ヘッダーとレコードの外の空行を除く）
     * @param counts 分類別の件数（受信記録から数えた値。キーのない隔離を含む。単位は受信記録の数で、受け取った件数（物理行）と違う）
     * @param rows 行ごとの結果（形式の正しい行と行の誤り。物理行の順）
     * @param unreadable 取り込めなかった部分（なければ {@code null}）
     */
    record Ingested(
            UUID ingestionId,
            int receivedCount,
            Map<Classification, Integer> counts,
            List<RowResult> rows,
            Unreadable unreadable)
            implements IngestionOutcome {

        public Ingested {
            Objects.requireNonNull(ingestionId, "ingestionId");
            counts = Map.copyOf(Objects.requireNonNull(counts, "counts"));
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }

        /** その分類の件数。 */
        public int countOf(Classification classification) {
            return counts.getOrDefault(classification, 0);
        }

        /**
         * 件数が突き合うか（BR-12 の件数照合の土台。Bolt 39b2 の確認ポイント 9）。受け取った件数 ＝ 行の結果の物理行の数の合計 ＋
         * 取り込めなかった物理行の数。単位はどちらも物理行。
         */
        public boolean reconciles() {
            int rowLines = rows.stream()
                    .mapToInt(row -> row.lastLineNumber() - row.lineNumber() + 1)
                    .sum();
            int unreadableLines = unreadable == null ? 0 : unreadable.lineCount();
            return receivedCount == rowLines + unreadableLines;
        }

        /**
         * 取込全体の要確認か。取り込めなかった物理行がある（引用符の閉じ忘れなど）か、ファイルの誤りの隔離がある（データ行がなくても）とき。
         * 行の誤りだけなら要確認にしない（その行の隔離で示す）。
         */
        public boolean requiresReview() {
            return unreadable != null;
        }
    }

    /**
     * 1 行の結果。
     *
     * @param lineNumber 行が始まる物理行の番号
     * @param lastLineNumber 行が終わる物理行の番号（Bolt 39b2）
     * @param classification 分類
     * @param externalRecordId 外部原本 ID（重複・隔離は既存の外部原本。US-14 AC3 の既存結果。行の誤りのキーのない隔離は {@code null}）
     * @param receivedRecordId 受信記録 ID
     */
    record RowResult(
            int lineNumber,
            int lastLineNumber,
            Classification classification,
            UUID externalRecordId,
            UUID receivedRecordId) {

        public RowResult {
            Objects.requireNonNull(classification, "classification");
            Objects.requireNonNull(receivedRecordId, "receivedRecordId");
            if (classification != Classification.QUARANTINED && externalRecordId == null) {
                throw new IllegalArgumentException("外部原本 ID を持たない行の結果は隔離だけ: " + classification);
            }
        }
    }

    /**
     * 取り込めなかった部分の結果（Bolt 39b2）。
     *
     * @param fromLine 取り込めなかった行の始まり（数が 0 なら {@code null}）
     * @param lineCount 取り込めなかった物理行の数
     * @param receivedRecordId キーのない隔離の受信記録 ID
     */
    record Unreadable(Integer fromLine, int lineCount, UUID receivedRecordId) {}

    /** 情報源が承認済みでない（候補・停止中。ED-INV-01、BR-18、US-14 AC2）。 */
    record DataSourceNotApproved() implements IngestionOutcome {}

    /** 情報源がない。 */
    record DataSourceNotFound() implements IngestionOutcome {}

    /** 操作者がデータ責任者でない（URL の認可に加えてサービスでも確かめる）。 */
    record NotPermitted() implements IngestionOutcome {}
}
