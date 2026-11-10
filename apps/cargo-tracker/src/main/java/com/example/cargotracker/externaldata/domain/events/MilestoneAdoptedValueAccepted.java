package com.example.cargotracker.externaldata.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-13 主要実績の採用値を受け付けた（US-14 AC1・AC4。Bolt 39b3）。取込で主要実績の採用値を追記したとき、取込と同じトランザクションで出し、採用の判断で
 * 主要実績の採用値を追記したとき（Bolt 39d3b2）、判断と同じトランザクションで DE-29 の後に出す。追跡（主要実績の下書き。Bolt 39g）と監査（取込での自動の採用は
 * 39b3b、採用の判断は 39d3b2）が購読する。追跡番号と種類は文字列で持つ（イベントは外部データの値の型を持たない）。
 * 発行元の集約の版は持たず公開した時刻の順で代える（Bolt 39g の【前の決定の変更】(12)）。出典の参照は受信記録 ID、取得時刻はファイルの値。
 *
 * @param adoptedValueId 採用値 ID
 * @param ingestionId 取込 ID
 * @param dataSourceId 情報源 ID
 * @param source 出典（種類は外部原本、参照は受信記録 ID、取得時刻はファイルの値）
 * @param trackingNumber 追跡番号
 * @param kind 実績の種類の名前（例 {@code DEPARTURE}）
 * @param location 場所（UN/LOCODE）
 * @param occurredAt 発生時刻（ファイルの値）
 * @param externalId 外部 ID
 * @param infoVersion 情報版（外部版）
 * @param operatorUserId 操作者（取込の操作者か判断者。データ責任者）の利用者 ID
 * @param operatorCompanyId 操作者の所属企業 ID
 * @param publishedAt 公開した時刻（取込での採用は取込の時刻、採用の判断は判断の時刻）
 */
@DomainEvent
public record MilestoneAdoptedValueAccepted(
        UUID adoptedValueId,
        UUID ingestionId,
        UUID dataSourceId,
        Source source,
        String trackingNumber,
        String kind,
        Location location,
        UtcInstant occurredAt,
        String externalId,
        String infoVersion,
        UserId operatorUserId,
        CompanyId operatorCompanyId,
        UtcInstant publishedAt) {}
