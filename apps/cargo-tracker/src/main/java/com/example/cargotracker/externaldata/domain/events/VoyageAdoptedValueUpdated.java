package com.example.cargotracker.externaldata.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.UUID;

/**
 * DE-12 航海の採用値を更新した（US-14 AC1。Bolt 39b3）。取込で航海の採用値を追記したとき、取込と同じトランザクションで出す。経路設計
 * （航海の更新。39e）と監査（取込での自動の採用。39b3b）が購読する。値の写しを Java の標準と共有カーネルの型だけで持ち、外部データの値の型を
 * 持たない。採用値は追記だけで版を持たないので、発行元の集約の版は持たず情報版で代える。出典の参照は受信記録 ID、取得時刻はファイルの値。
 *
 * @param adoptedValueId 採用値 ID
 * @param ingestionId 取込 ID
 * @param dataSourceId 情報源 ID
 * @param source 出典（種類は外部原本、参照は受信記録 ID、取得時刻はファイルの値）
 * @param voyageNumber 航海番号
 * @param portCalls 寄港の並び（運航の順）
 * @param infoVersion 情報版（外部版）
 * @param operatorUserId 操作者（データ責任者）の利用者 ID
 * @param operatorCompanyId 操作者の所属企業 ID
 * @param publishedAt 公開した時刻（取込の時刻）
 */
@DomainEvent
public record VoyageAdoptedValueUpdated(
        UUID adoptedValueId,
        UUID ingestionId,
        UUID dataSourceId,
        Source source,
        String voyageNumber,
        List<AdoptedPortCall> portCalls,
        String infoVersion,
        UserId operatorUserId,
        CompanyId operatorCompanyId,
        UtcInstant publishedAt) {

    public VoyageAdoptedValueUpdated {
        portCalls = List.copyOf(portCalls);
    }

    /**
     * 採用した寄港。航海の採用値の寄港の 1 つの写し（経路設計の {@code PortCall} と同じ単純名にしない。Bolt 39b2b の確認ポイント 1）。
     *
     * @param port 港（UN/LOCODE）
     * @param arrivalAt 到着（ないときは null）
     * @param departureAt 出発（ないときは null）
     */
    public record AdoptedPortCall(Location port, UtcInstant arrivalAt, UtcInstant departureAt) {}
}
