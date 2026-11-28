package com.example.cargotracker.externaldata.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-14 外部原本を隔離した（US-14 AC4、UC-15 2a。Bolt 39b3）。受信記録を隔離にしたとき、取込と同じトランザクションで出す。内容の違う原本の
 * 隔離に加え、行の誤り・ファイルの誤り・取り込めなかった部分のキーのない隔離にも同じイベントを出す（外部 ID・外部版は null）。重複には出さない。
 * 監査（39b3b）とデータ責任者への表示（39c・39d）が購読する。理由の区分は文字列で、内容の違う原本は {@link #CONTENT_MISMATCH}、形式の誤りは
 * 形式の誤りの名前（例 {@code COLUMN_COUNT_MISMATCH}）。物理行の範囲はファイルの誤りでは null。
 *
 * @param receivedRecordId 受信記録 ID
 * @param ingestionId 取込 ID
 * @param dataSourceId 情報源 ID
 * @param provider 提供元
 * @param recordType 原本種別
 * @param externalId 外部 ID（キーのない隔離は null）
 * @param externalVersion 外部版（キーのない隔離は null）
 * @param reason 理由の区分
 * @param firstLine 物理行の範囲の始まり（ファイルの誤りは null）
 * @param lastLine 物理行の範囲の終わり（ファイルの誤りは null）
 * @param operatorUserId 操作者（データ責任者）の利用者 ID
 * @param operatorCompanyId 操作者の所属企業 ID
 * @param quarantinedAt 隔離した時刻（取込の時刻）
 */
@DomainEvent
public record ExternalRecordQuarantined(
        UUID receivedRecordId,
        UUID ingestionId,
        UUID dataSourceId,
        String provider,
        String recordType,
        String externalId,
        String externalVersion,
        String reason,
        Integer firstLine,
        Integer lastLine,
        UserId operatorUserId,
        CompanyId operatorCompanyId,
        UtcInstant quarantinedAt) {

    /** 理由の区分: 同じ外部 ID・外部版で内容の違う原本（冪等性キーのある隔離）。 */
    public static final String CONTENT_MISMATCH = "CONTENT_MISMATCH";
}
