package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.shared.domain.Source;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * 外部原本の採否の監査のイベントハンドラー。外部データの DE-12・DE-13・DE-14 を取込のコミットの後に別のトランザクションで受け、受信記録ごとに
 * 外部原本の採否の監査記録を 1 つ書く（Bolt 39b3b。AUD-01・AUD-02、IA-INV-15、ADR-003・ADR-004）。操作者と発生時刻はイベントのもの。
 * 配信は少なくとも 1 回なので、操作と対象（受信記録）で引いて記録があれば書かない。内容の欠けたイベントは再配信しても直らないので、ERROR の
 * ログを残して捨てる。DB の失敗は例外を上げ、発行記録を未完了に残して起動のときに配り直させる（前例 {@link KpiObservationEventHandler}）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class ExternalRecordAuditEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalRecordAuditEventHandler.class);

    /** 監査記録の対象の種類（受信記録）。 */
    private static final String RECEIVED_RECORD = "RECEIVED_RECORD";

    /** 要約の値がないときの印（キーのない隔離の外部 ID・外部版、ファイルの誤りの行）。 */
    private static final String NONE = "-";

    private final AuditRecordRepository auditRecords;

    public ExternalRecordAuditEventHandler(AuditRecordRepository auditRecords) {
        this.auditRecords = auditRecords;
    }

    /** DE-12 航海の採用値を更新した を受けて、取込での自動の採用を監査する。対象は出典の参照（受信記録 ID）。 */
    @ApplicationModuleListener
    public void on(VoyageAdoptedValueUpdated event) {
        String receivedRecordId = reference(event.source());
        appendOnce(
                AuditAction.EXTERNAL_RECORD_ADOPTED,
                receivedRecordId,
                event.ingestionId(),
                () -> AuditRecord.externalRecordAdopted(
                        event.operatorUserId(),
                        event.operatorCompanyId(),
                        receivedRecordId,
                        "recordType=VOYAGE;targetKey=" + event.voyageNumber()
                                + adoptedTail(
                                        event.infoVersion(),
                                        event.ingestionId(),
                                        event.dataSourceId(),
                                        event.adoptedValueId()),
                        event.publishedAt()));
    }

    /** DE-13 主要実績の採用値を受け付けた を受けて、取込での自動の採用を監査する。要約に追跡番号と外部 ID を入れる。 */
    @ApplicationModuleListener
    public void on(MilestoneAdoptedValueAccepted event) {
        String receivedRecordId = reference(event.source());
        appendOnce(
                AuditAction.EXTERNAL_RECORD_ADOPTED,
                receivedRecordId,
                event.ingestionId(),
                () -> AuditRecord.externalRecordAdopted(
                        event.operatorUserId(),
                        event.operatorCompanyId(),
                        receivedRecordId,
                        "recordType=MILESTONE;targetKey=" + event.trackingNumber() + ";externalId=" + event.externalId()
                                + adoptedTail(
                                        event.infoVersion(),
                                        event.ingestionId(),
                                        event.dataSourceId(),
                                        event.adoptedValueId()),
                        event.publishedAt()));
    }

    /** DE-14 外部原本を隔離した を受けて、隔離を監査する。理由は隔離の理由の区分。外部版は要約に入れ、対象の版には入れない。 */
    @ApplicationModuleListener
    public void on(ExternalRecordQuarantined event) {
        String receivedRecordId = Objects.toString(event.receivedRecordId(), null);
        appendOnce(
                AuditAction.EXTERNAL_RECORD_QUARANTINED,
                receivedRecordId,
                event.ingestionId(),
                () -> AuditRecord.externalRecordQuarantined(
                        event.operatorUserId(),
                        event.operatorCompanyId(),
                        receivedRecordId,
                        event.reason(),
                        "provider=" + event.provider()
                                + ";recordType=" + event.recordType()
                                + ";externalId=" + orNone(event.externalId())
                                + ";externalVersion=" + orNone(event.externalVersion())
                                + ";lines=" + lines(event.firstLine(), event.lastLine())
                                + ";ingestionId=" + event.ingestionId()
                                + ";dataSourceId=" + event.dataSourceId(),
                        event.quarantinedAt()));
    }

    private void appendOnce(
            AuditAction action, String receivedRecordId, UUID ingestionId, Supplier<AuditRecord> create) {
        if (auditRecords.existsByActionAndTarget(action, RECEIVED_RECORD, receivedRecordId)) {
            return;
        }
        AuditRecord auditRecord;
        try {
            auditRecord = create.get();
        } catch (IllegalArgumentException e) {
            LOG.error(
                    "外部原本の採否の監査記録を書かずに捨てた（イベントの内容が欠けている。再配信しても直らない。調査が要る）:" + " 操作 {}、受信記録 {}、取込 {}、欠け {}",
                    action,
                    receivedRecordId,
                    ingestionId,
                    e.getMessage());
            return;
        }
        auditRecords.append(auditRecord);
    }

    private static String reference(Source source) {
        return source == null ? null : source.reference();
    }

    private static String adoptedTail(String infoVersion, UUID ingestionId, UUID dataSourceId, UUID adoptedValueId) {
        return ";infoVersion=" + infoVersion
                + ";ingestionId=" + ingestionId
                + ";dataSourceId=" + dataSourceId
                + ";adoptedValueId=" + adoptedValueId;
    }

    private static String orNone(String value) {
        return value == null ? NONE : value;
    }

    private static String lines(Integer firstLine, Integer lastLine) {
        if (firstLine == null || lastLine == null) {
            return NONE;
        }
        return firstLine + "-" + lastLine;
    }
}
