package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 取込。データ責任者が 1 ファイルを取り込む 1 回の単位（data_model の {@code external_data.ingestion}。Bolt 39b）。リポジトリの
 * ポートが受け渡す record で、振る舞いを持たない（トランザクションスクリプト。ADR-002）。追記専用で、書いた後に更新しない。
 *
 * @param id 取込 ID
 * @param dataSourceId 情報源 ID
 * @param fileObjectKey ファイル全体の原本のオブジェクトキー
 * @param fileHash ファイル全体のハッシュ
 * @param ingestedBy 取込者
 * @param ingestedAt 取込時刻
 * @param receivedCount 受け取った件数（ファイルの物理データ行の数。ヘッダーを除く）
 */
@Entity
public record Ingestion(
        UUID id,
        UUID dataSourceId,
        PayloadObjectKey fileObjectKey,
        PayloadHash fileHash,
        UserId ingestedBy,
        Instant ingestedAt,
        int receivedCount) {

    public Ingestion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(fileObjectKey, "fileObjectKey");
        Objects.requireNonNull(fileHash, "fileHash");
        Objects.requireNonNull(ingestedBy, "ingestedBy");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        if (receivedCount < 0) {
            throw new IllegalArgumentException("受け取った件数は 0 以上: " + receivedCount);
        }
    }
}
