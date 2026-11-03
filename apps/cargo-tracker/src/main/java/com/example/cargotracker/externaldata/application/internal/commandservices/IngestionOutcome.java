package com.example.cargotracker.externaldata.application.internal.commandservices;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 取込の結果（{@link IngestionService#ingest}。Bolt 39b）。拒否のときは原本も含めて何も保存しない（例外にせず種類で返す）。
 */
public sealed interface IngestionOutcome {

    /**
     * 取り込んだ。
     *
     * @param ingestionId 取込 ID
     * @param receivedCount 受け取った件数（ファイルの物理データ行の数。ヘッダーを除く）
     * @param counts 分類別の件数（受信記録から数えた値）
     * @param rows 行ごとの結果（物理行の順）
     */
    record Ingested(UUID ingestionId, int receivedCount, Map<Classification, Integer> counts, List<RowResult> rows)
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
    }

    /**
     * 1 行の結果。
     *
     * @param lineNumber 物理行の番号
     * @param classification 分類
     * @param externalRecordId 外部原本 ID（重複・隔離は既存の外部原本。US-14 AC3 の既存結果）
     * @param receivedRecordId 受信記録 ID
     */
    record RowResult(int lineNumber, Classification classification, UUID externalRecordId, UUID receivedRecordId) {

        public RowResult {
            Objects.requireNonNull(classification, "classification");
            Objects.requireNonNull(externalRecordId, "externalRecordId");
            Objects.requireNonNull(receivedRecordId, "receivedRecordId");
        }
    }

    /** 情報源が承認済みでない（候補・停止中。ED-INV-01、BR-18、US-14 AC2）。 */
    record DataSourceNotApproved() implements IngestionOutcome {}

    /** 情報源がない。 */
    record DataSourceNotFound() implements IngestionOutcome {}

    /** 操作者がデータ責任者でない（URL の認可に加えてサービスでも確かめる）。 */
    record NotPermitted() implements IngestionOutcome {}
}
