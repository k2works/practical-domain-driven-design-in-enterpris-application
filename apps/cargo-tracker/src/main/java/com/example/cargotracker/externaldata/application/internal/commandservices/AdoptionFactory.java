package com.example.cargotracker.externaldata.application.internal.commandservices;

import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated.AdoptedPortCall;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues;
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
 * 採用の結果の組み立て役（Bolt 39b3）。採用の受信記録と冪等性キーと受け取った値から、採用値とそのイベント（航海は DE-12、主要実績は DE-13）を
 * 作る（取込の行を受けない。Bolt 39d3c の開始準備の決定 (9)。取込と採用の判断（Bolt 39d3b）が同じ形で呼ぶ）。対象の種類は受け取った値の型から、
 * 対象のキーは受け取った値の {@link RecordValues#targetKey()}（航海は航海番号、主要実績は追跡番号）、要約は受け取った値から作り、情報版は
 * 冪等性キーの外部版、公開した時刻は呼び出しが渡す時刻。取込 ID と取得時刻は受信記録から読む。値の型の分岐はイベントの組み立てだけに残す
 * （DE-12 は寄港の並び、DE-13 は種類・場所・発生時刻を持つ）。イベントは値の写しを Java の標準と共有カーネルの型で持ち、出典の参照は受信記録
 * ID、取得時刻は受信記録の取得時刻。書き込みと発行は呼び出しが行う。
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
     * @param received 採用の受信記録（取込 ID と取得時刻を読む）
     * @param key 外部原本の冪等性キー（外部 ID と外部版を読む）
     * @param values 受け取った値
     * @param dataSourceId 情報源 ID
     * @param operator 操作者（データ責任者）
     * @param publishedAt 公開した時刻（取込での採用は取込の時刻、採用の判断は判断の時刻）
     * @return 採用の結果
     */
    static Adoption adopt(
            ReceivedRecord received,
            IdempotencyKey key,
            RecordValues values,
            UUID dataSourceId,
            AuthenticatedActor operator,
            Instant publishedAt) {
        UUID adoptedValueId = UUID.randomUUID();
        Source origin =
                new Source(SourceKind.EXTERNAL_RECORD, received.id().toString(), new UtcInstant(received.acquiredAt()));
        UtcInstant published = new UtcInstant(publishedAt);
        return switch (values) {
            case VoyageValues voyage ->
                new Adoption(
                        adoptedValue(adoptedValueId, received, key, TargetType.VOYAGE, values, publishedAt),
                        new VoyageAdoptedValueUpdated(
                                adoptedValueId,
                                received.ingestionId(),
                                dataSourceId,
                                origin,
                                voyage.voyageNumber(),
                                voyage.portCalls().stream()
                                        .map(call ->
                                                new AdoptedPortCall(call.port(), call.arrivalAt(), call.departureAt()))
                                        .toList(),
                                key.externalVersion(),
                                operator.userId(),
                                operator.companyId(),
                                published));
            case MilestoneValues(
                    TrackingNumberValue trackingNumber,
                    MilestoneKindValue kind,
                    Location location,
                    UtcInstant occurredAt) ->
                new Adoption(
                        adoptedValue(adoptedValueId, received, key, TargetType.MILESTONE, values, publishedAt),
                        new MilestoneAdoptedValueAccepted(
                                adoptedValueId,
                                received.ingestionId(),
                                dataSourceId,
                                origin,
                                trackingNumber.value(),
                                kind.name(),
                                location,
                                occurredAt,
                                key.externalId(),
                                key.externalVersion(),
                                operator.userId(),
                                operator.companyId(),
                                published));
        };
    }

    private static AdoptedValue adoptedValue(
            UUID id,
            ReceivedRecord received,
            IdempotencyKey key,
            TargetType targetType,
            RecordValues values,
            Instant publishedAt) {
        return new AdoptedValue(
                id,
                received.id(),
                targetType,
                values.targetKey(),
                values.summary(),
                key.externalVersion(),
                publishedAt);
    }
}
