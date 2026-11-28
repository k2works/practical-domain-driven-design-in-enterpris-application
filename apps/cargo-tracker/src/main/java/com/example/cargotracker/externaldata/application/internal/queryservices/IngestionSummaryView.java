package com.example.cargotracker.externaldata.application.internal.queryservices;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 取込の要約。取込の要約の照会の値（S-16 の結果の画面。Bolt 39c）。取込のサービスの結果（取込の結果 {@code IngestionOutcome}）とは別で、
 * DB から読み直した値。受け取った件数は物理データ行で、分類別の件数（受信記録の数）と単位が違う。隔離の理由（注記）は行の順に最初の
 * {@link IngestionSummaryQueryService#SHOWN_QUARANTINE_LIMIT} 件と、示さなかった隔離の数を持つ（Bolt 39c3）。取込全体の要確認と取り込めなかった
 * 部分を持つ（Bolt 39c4）。
 *
 * @param ingestionId 取込 ID
 * @param provider 情報源の提供元
 * @param recordType 情報源の原本種別（取込ファイルの種別の名前）
 * @param ingestedAt 取り込んだ日時（取込の行の取込時刻）
 * @param receivedCount 受け取った件数（物理データ行）
 * @param adoptedCount 採用の受信記録の数
 * @param duplicateCount 重複の受信記録の数
 * @param quarantinedCount 隔離の受信記録の数（キーのない隔離を含む）
 * @param discrepancyCount 差異の受信記録の数（Bolt 39h）
 * @param quarantineNotes 示す隔離と差異の注記（ファイルの行の順。範囲のないファイルの誤りが先。差異は Bolt 39h）
 * @param remainingQuarantineCount 示さなかった隔離と差異の数（隔離と差異の件数の和 − 示した数）
 * @param unreadable 取り込めなかった部分（要確認でなければ {@code null}。Bolt 39c4）
 */
public record IngestionSummaryView(
        UUID ingestionId,
        String provider,
        String recordType,
        Instant ingestedAt,
        int receivedCount,
        int adoptedCount,
        int duplicateCount,
        int quarantinedCount,
        int discrepancyCount,
        List<String> quarantineNotes,
        int remainingQuarantineCount,
        UnreadableRegion unreadable) {

    public IngestionSummaryView {
        Objects.requireNonNull(ingestionId, "ingestionId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(recordType, "recordType");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        quarantineNotes = List.copyOf(Objects.requireNonNull(quarantineNotes, "quarantineNotes"));
    }

    /**
     * 取込全体の要確認か。取り込めなかった部分があるとき（取込の結果の {@code IngestionOutcome.Ingested#requiresReview()} と同じ形。
     * 行の誤りだけなら要確認にしない）。
     */
    public boolean requiresReview() {
        return unreadable != null;
    }

    /**
     * 取り込めなかった部分（Bolt 39c4 の開始準備の決定 (3)）。取込の結果の {@code IngestionOutcome.Unreadable} と違い、受信記録 ID を持たず、
     * ファイルの誤りかを持つ。
     *
     * @param fromLine 取り込めなかった行の始まり（取込の列のまま。データ行のないファイルの誤りは {@code null}）
     * @param lineCount 取り込めなかった物理行の数（取込の列のまま。ファイルの誤りは受け取った件数）
     * @param wholeFile ファイルの誤りか（ファイル全体の原本を指す隔離がある）
     */
    public record UnreadableRegion(Integer fromLine, int lineCount, boolean wholeFile) {}
}
