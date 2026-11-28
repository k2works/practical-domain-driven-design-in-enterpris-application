package com.example.cargotracker.externaldata.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-31 外部原本の差異を見つけた（US-14 AC5、ED-INV-03。Bolt 39h の開始準備の決定 (8)）。取込で受信記録を差異にしたとき、取込と同じ
 * トランザクションで出す。差異は隔離（DE-14）と別の事実で、採用値も DE-12・DE-13・DE-14 も伴わない。購読するのはアクセス・監査だけで、
 * 経路設計と追跡は購読しない。理由の区分は差異の理由の名前の文字列（{@code VERSION_REVERSED}・{@code TARGET_KEY_DIFFERS}。イベントは
 * ドメインの型を持たない）。
 *
 * @param receivedRecordId 受信記録 ID
 * @param ingestionId 取込 ID
 * @param dataSourceId 情報源 ID
 * @param provider 提供元
 * @param recordType 原本種別
 * @param externalId 外部 ID
 * @param externalVersion 外部版（今受け取った原本の外部版）
 * @param reason 差異の理由の区分
 * @param comparedExternalVersion 比べた相手（この外部 ID の採用値）の外部版
 * @param firstLine 物理行の範囲の始まり
 * @param lastLine 物理行の範囲の終わり
 * @param operatorUserId 操作者（データ責任者）の利用者 ID
 * @param operatorCompanyId 操作者の所属企業 ID
 * @param foundAt 見つけた時刻（取込の時刻）
 */
@DomainEvent
public record ExternalRecordDiscrepancyFound(
        UUID receivedRecordId,
        UUID ingestionId,
        UUID dataSourceId,
        String provider,
        String recordType,
        String externalId,
        String externalVersion,
        String reason,
        String comparedExternalVersion,
        Integer firstLine,
        Integer lastLine,
        UserId operatorUserId,
        CompanyId operatorCompanyId,
        UtcInstant foundAt) {}
