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
 * @param unparsedFromLine 取り込めなかった行の始まり（物理行の番号。取り込めなかった行がなければ {@code null}。Bolt 39b2）
 * @param unparsedLineCount 取り込めなかった物理行の数（数が 0 なら始まりは {@code null}、0 より大きければ始まりを持つ。表の CHECK
 *     {@code ck_ingestion_unparsed_lines} と同じ規則。Bolt 39b2）
 */
@Entity
public record Ingestion(
        UUID id,
        UUID dataSourceId,
        PayloadObjectKey fileObjectKey,
        PayloadHash fileHash,
        UserId ingestedBy,
        Instant ingestedAt,
        int receivedCount,
        Integer unparsedFromLine,
        int unparsedLineCount) {

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
        boolean none = unparsedLineCount == 0 && unparsedFromLine == null;
        boolean some = unparsedLineCount > 0 && unparsedFromLine != null;
        if (!none && !some) {
            throw new IllegalArgumentException("取り込めなかった行の始まりと数が合わない: " + unparsedFromLine + "・" + unparsedLineCount);
        }
    }
}
