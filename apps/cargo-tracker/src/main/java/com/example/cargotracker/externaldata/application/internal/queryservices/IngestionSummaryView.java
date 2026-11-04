package com.example.cargotracker.externaldata.application.internal.queryservices;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 取込の要約。取込の要約の照会の値（S-16 の結果の画面。Bolt 39c）。取込のサービスの結果（取込の結果 {@code IngestionOutcome}）とは別で、
 * DB から読み直した値。受け取った件数は物理データ行で、分類別の件数（受信記録の数）と単位が違う。要確認と取り込めなかった部分は 39c3 で足す。
 *
 * @param ingestionId 取込 ID
 * @param provider 情報源の提供元
 * @param recordType 情報源の原本種別（取込ファイルの種別の名前）
 * @param ingestedAt 取り込んだ日時（取込の行の取込時刻）
 * @param receivedCount 受け取った件数（物理データ行）
 * @param adoptedCount 採用の受信記録の数
 * @param duplicateCount 重複の受信記録の数
 * @param quarantinedCount 隔離の受信記録の数（キーのない隔離を含む）
 */
public record IngestionSummaryView(
        UUID ingestionId,
        String provider,
        String recordType,
        Instant ingestedAt,
        int receivedCount,
        int adoptedCount,
        int duplicateCount,
        int quarantinedCount) {

    public IngestionSummaryView {
        Objects.requireNonNull(ingestionId, "ingestionId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(recordType, "recordType");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
    }
}
