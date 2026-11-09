package com.example.cargotracker.externaldata.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-29 受信記録を判断した（US-14 AC4、UC-15 3b。Bolt 39d2）。データ責任者が隔離の受信記録を判断したとき、判断と同じトランザクションで
 * 判断ごとに 1 つ出す。監査（identity の {@code ExternalRecordAuditEventHandler}。{@code EXTERNAL_RECORD_DECIDED}）が購読する。判断は
 * 判断の種類の名前の文字列（{@code REJECTED} か {@code ADOPTED}。採用は Bolt 39d3b）で、イベントはドメインの型を持たない（DomainEventArchitectureTest）。
 * 判断者の企業は監査記録が操作者の企業を必須にするために持つ（DE-14 の {@code operatorCompanyId} にそろえる）。
 *
 * @param receivedRecordId 受信記録 ID
 * @param ingestionId 取込 ID
 * @param dataSourceId 情報源 ID（取込から読む）
 * @param decision 判断（判断の種類の名前）
 * @param reason 理由（採用は任意で、ないときは {@code null}）
 * @param decidedBy 判断者（データ責任者）の利用者 ID
 * @param decidedByCompanyId 判断者の所属企業 ID
 * @param decidedAt 判断の時刻
 */
@DomainEvent
public record ReceivedRecordDecided(
        UUID receivedRecordId,
        UUID ingestionId,
        UUID dataSourceId,
        String decision,
        String reason,
        UserId decidedBy,
        CompanyId decidedByCompanyId,
        UtcInstant decidedAt) {}
