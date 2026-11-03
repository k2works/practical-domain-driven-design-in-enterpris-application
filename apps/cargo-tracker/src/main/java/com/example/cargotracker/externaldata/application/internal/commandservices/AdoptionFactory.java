package com.example.cargotracker.externaldata.application.internal.commandservices;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated.AdoptedPortCall;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;

/**
 * 採用の結果の組み立て役（Bolt 39b3）。採用の受信記録から、採用値とそのイベント（航海は DE-12、主要実績は DE-13）を取込の値の型の分岐
 * 1 か所で作る（取込のサービスの開発レビュー）。対象の種類とキーは取込の値の型から決め（航海は航海番号、主要実績は追跡番号）、要約は取込の
 * 値から作り、情報版は外部版、公開した時刻は取込の時刻。イベントは値の写しを Java の標準と共有カーネルの型で持ち、出典の参照は受信記録
 * ID、取得時刻はファイルの値。書き込みと発行は取込のサービスが行う。
 */
final class AdoptionFactory {

    private AdoptionFactory() {}

    /**
     * 採用の結果。
     *
     * @param adoptedValue 採用値
     * @param event 採用値のイベント（DE-12 か DE-13）
     */
    record Adoption(AdoptedValue adoptedValue, Object event) {}

    /**
     * 採用の受信記録の採用値とイベントを作る。
     *
     * @param received 採用の受信記録
     * @param row 取込の行
     * @param ingestionId 取込 ID
     * @param dataSourceId 情報源 ID
     * @param operator 操作者（データ責任者）
     * @param now 取込の時刻
     * @return 採用の結果
     */
    static Adoption adopt(
            ReceivedRecord received,
            IngestedRow row,
            UUID ingestionId,
            UUID dataSourceId,
            AuthenticatedActor operator,
            Instant now) {
        UUID adoptedValueId = UUID.randomUUID();
        Source origin =
                new Source(SourceKind.EXTERNAL_RECORD, received.id().toString(), new UtcInstant(row.acquiredAt()));
        UtcInstant publishedAt = new UtcInstant(now);
        return switch (row.values()) {
            case VoyageValues voyage ->
                new Adoption(
                        adoptedValue(adoptedValueId, received, row, TargetType.VOYAGE, voyage.voyageNumber(), now),
                        new VoyageAdoptedValueUpdated(
                                adoptedValueId,
                                ingestionId,
                                dataSourceId,
                                origin,
                                voyage.voyageNumber(),
                                voyage.portCalls().stream()
                                        .map(call ->
                                                new AdoptedPortCall(call.port(), call.arrivalAt(), call.departureAt()))
                                        .toList(),
                                row.externalVersion(),
                                operator.userId(),
                                operator.companyId(),
                                publishedAt));
            case MilestoneValues(
                    TrackingNumberValue trackingNumber,
                    MilestoneKindValue kind,
                    Location location,
                    UtcInstant occurredAt) ->
                new Adoption(
                        adoptedValue(adoptedValueId, received, row, TargetType.MILESTONE, trackingNumber.value(), now),
                        new MilestoneAdoptedValueAccepted(
                                adoptedValueId,
                                ingestionId,
                                dataSourceId,
                                origin,
                                trackingNumber.value(),
                                kind.name(),
                                location,
                                occurredAt,
                                row.externalId(),
                                row.externalVersion(),
                                operator.userId(),
                                operator.companyId(),
                                publishedAt));
        };
    }

    private static AdoptedValue adoptedValue(
            UUID id, ReceivedRecord received, IngestedRow row, TargetType targetType, String targetKey, Instant now) {
        return new AdoptedValue(
                id, received.id(), targetType, targetKey, row.values().summary(), row.externalVersion(), now);
    }
}
