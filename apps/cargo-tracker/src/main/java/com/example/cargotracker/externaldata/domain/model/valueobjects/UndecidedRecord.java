package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 判断を待つ隔離。分類が隔離で、受信記録の判断の行がない受信記録を、S-17 の一覧（Bolt 39d）に示すために取込と外部原本を結んで読む照会専用の
 * record（前例 {@code RoutingCaseSummary}）。受信記録の record（{@code ReceivedRecord}）には外部 ID・外部版を足さない（Bolt 39d 計画の
 * 開始準備の決定 (4)）。外部原本を持たないキーのない隔離（形式の誤り・ファイルの誤り・取り込めなかった部分）は外部 ID・外部版を持たない。
 *
 * @param receivedRecordId 受信記録 ID
 * @param ingestionId 取込 ID
 * @param ingestedAt 取込時刻（一覧の並びと取り込んだ日時）
 * @param dataSourceId 情報源 ID（取込の情報源）
 * @param lineRange 行の範囲（ファイルの誤りと Bolt 39c3 より前の受信記録は {@code null}）
 * @param externalId 外部 ID（外部原本のないときは {@code null}）
 * @param externalVersion 外部版（外部原本のないときは {@code null}）
 * @param note 分類の注記（隔離の理由）
 */
@ValueObject
public record UndecidedQuarantine(
        UUID receivedRecordId,
        UUID ingestionId,
        Instant ingestedAt,
        UUID dataSourceId,
        PhysicalLineRange lineRange,
        String externalId,
        String externalVersion,
        String note) {

    public UndecidedQuarantine {
        Objects.requireNonNull(receivedRecordId, "receivedRecordId");
        Objects.requireNonNull(ingestionId, "ingestionId");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        if ((externalId == null) != (externalVersion == null)) {
            throw new IllegalArgumentException("外部 ID と外部版はそろって持つかそろって持たない: " + receivedRecordId);
        }
    }

    /** 外部原本を持つか（内容の違う原本）。持たなければキーのない隔離（形式の誤り）。 */
    public boolean hasExternalRecord() {
        return externalId != null;
    }
}
