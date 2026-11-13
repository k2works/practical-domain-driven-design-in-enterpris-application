package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.externaldata.domain.events.ExternalRecordDiscrepancyFound;
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.ReceivedRecordDecided;
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
 * 外部原本の採否の監査のイベントハンドラー。外部データの DE-12・DE-13・DE-14・DE-31（Bolt 39h）を取込のコミットの後（DE-12・DE-13 は採用の判断のコミットの後にも。
 * Bolt 39d3b）に別のトランザクションで受け、受信記録ごとに外部原本の採否の監査記録を 1 つ書く（Bolt 39b3b。AUD-01・AUD-02、IA-INV-15、ADR-003・
 * ADR-004）。DE-29（データ責任者の受信記録の判断）も判断のコミットの後に受け、（操作、受信記録）ごとに 1 つ書く（Bolt 39d2）。操作者と発生時刻は
 * イベントのもの。
 * 配信は少なくとも 1 回なので、操作と対象（受信記録）で引いて記録があれば書かない。内容の欠けたイベント
 * （{@link AuditRecord.MissingEventValueException}）は再配信しても直らないので、ERROR のログを残して捨てる。DB の失敗とほかの例外（プログラムの
 * 誤り）は上げ、発行記録を未完了に残して起動のときに配り直させる（前例 {@link KpiObservationEventHandler}）。
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

    /** 要約の外部 ID の部品の名前（隔離・差異・主要実績の採用で共通）。 */
    private static final String EXTERNAL_ID_PART = ";externalId=";

    /** DE-29 の判断のうち、理由が任意の採用（イベントは判断を種類の名前の文字列で持つ）。 */
    private static final String ADOPTED = "ADOPTED";

    private final AuditRecordRepository auditRecords;

    public ExternalRecordAuditEventHandler(AuditRecordRepository auditRecords) {
        this.auditRecords = auditRecords;
    }

    /** DE-12 航海の採用値を更新した を受けて、取込での自動の採用と採用の判断を監査する。対象は出典の参照（受信記録 ID）。 */
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
                        "recordType=VOYAGE;targetKey=" + part(event.voyageNumber(), "voyageNumber")
                                + adoptedTail(
                                        event.infoVersion(),
                                        event.ingestionId(),
                                        event.dataSourceId(),
                                        event.adoptedValueId()),
                        event.publishedAt()));
    }

    /** DE-13 主要実績の採用値を受け付けた を受けて、取込での自動の採用と採用の判断を監査する。対象は出典の参照（受信記録 ID）。要約に追跡番号と外部 ID を入れる。 */
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
                        "recordType=MILESTONE;targetKey=" + part(event.trackingNumber(), "trackingNumber")
                                + EXTERNAL_ID_PART + part(event.externalId(), "externalId")
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
                        "provider=" + part(event.provider(), "provider")
                                + ";recordType=" + part(event.recordType(), "recordType")
                                + EXTERNAL_ID_PART + orNone(event.externalId())
                                + ";externalVersion=" + orNone(event.externalVersion())
                                + ";lines=" + lines(event.firstLine(), event.lastLine())
                                + origin(event.ingestionId(), event.dataSourceId()),
                        event.quarantinedAt()));
    }

    /**
     * DE-31 外部原本の差異を見つけた を受けて、差異を監査する（Bolt 39h）。理由は差異の理由の区分。要約は DE-14 と同じ形に、外部版の後に比べた外部版を
     * 入れる。
     */
    @ApplicationModuleListener
    public void on(ExternalRecordDiscrepancyFound event) {
        String receivedRecordId = Objects.toString(event.receivedRecordId(), null);
        appendOnce(
                AuditAction.EXTERNAL_RECORD_DISCREPANCY_FOUND,
                receivedRecordId,
                event.ingestionId(),
                () -> AuditRecord.externalRecordDiscrepancyFound(
                        event.operatorUserId(),
                        event.operatorCompanyId(),
                        receivedRecordId,
                        event.reason(),
                        "provider=" + part(event.provider(), "provider")
                                + ";recordType=" + part(event.recordType(), "recordType")
                                + EXTERNAL_ID_PART + part(event.externalId(), "externalId")
                                + ";externalVersion=" + part(event.externalVersion(), "externalVersion")
                                + ";comparedExternalVersion="
                                + part(event.comparedExternalVersion(), "comparedExternalVersion")
                                + ";lines=" + lines(event.firstLine(), event.lastLine())
                                + origin(event.ingestionId(), event.dataSourceId()),
                        event.foundAt()));
    }

    /**
     * DE-29 受信記録を判断した を受けて、データ責任者の判断を監査する（Bolt 39d2）。理由は判断、判断の理由の文は要約の最後に置く（理由の文に
     * 区切りの {@code ;} があっても最後なので読み違えない）。採用の理由は任意なので、理由のない採用は要約から理由の部品を省く（Bolt 39d3b）。
     */
    @ApplicationModuleListener
    public void on(ReceivedRecordDecided event) {
        String receivedRecordId = Objects.toString(event.receivedRecordId(), null);
        appendOnce(
                AuditAction.EXTERNAL_RECORD_DECIDED,
                receivedRecordId,
                event.ingestionId(),
                () -> AuditRecord.externalRecordDecided(
                        event.decidedBy(),
                        event.decidedByCompanyId(),
                        receivedRecordId,
                        event.decision(),
                        "decision=" + part(event.decision(), "decision")
                                + origin(event.ingestionId(), event.dataSourceId())
                                + versionReversedPart(event)
                                + reasonPart(event),
                        event.decidedAt()));
    }

    /**
     * 要約の版の逆転の部品（Bolt 40-2。W7 週全体レビュー M5）。版の逆転の隔離を採用したときだけ、上書きしたことと比べた採用値の外部版を残し、
     * 後から「戻した」ことを追えるようにする。逆転でなければ部品を省く（今までの形のまま）。逆転なのに比べた外部版がなければ内容の欠け。
     */
    private static String versionReversedPart(ReceivedRecordDecided event) {
        if (!event.versionReversed()) {
            return "";
        }
        return ";versionReversed=true;comparedExternalVersion="
                + part(event.comparedExternalVersion(), "comparedExternalVersion");
    }

    /**
     * 要約の判断の理由の部品。理由のない採用は部品を省く（理由は利用者の書く文字列なので、{@link #NONE} の印と区別できない。Bolt 39d3b の開始準備の
     * 決定 (7)）。却下の理由は必須なので、ほかの欠けは内容の欠けとして扱う。
     */
    private static String reasonPart(ReceivedRecordDecided event) {
        boolean withoutReason = event.reason() == null || event.reason().isBlank();
        if (withoutReason && ADOPTED.equals(event.decision())) {
            return "";
        }
        return ";reason=" + part(event.reason(), "reason");
    }

    /**
     * 内容を確かめて（記録を作って）から照会し、なければ追記する。内容の欠けは {@link AuditRecord.MissingEventValueException} だけで、
     * 受信記録 ID がなければ DB に問い合わせない。ほかの例外（プログラムの誤り・DB の失敗）は捕まえずに上げ、発行記録を未完了に残す。
     */
    private void appendOnce(
            AuditAction action, String receivedRecordId, UUID ingestionId, Supplier<AuditRecord> create) {
        AuditRecord auditRecord;
        try {
            auditRecord = create.get();
        } catch (AuditRecord.MissingEventValueException e) {
            LOG.error(
                    "外部原本の採否の監査記録を書かずに捨てた（イベントの内容が欠けている。再配信しても直らない。調査が要る）:" + " 操作 {}、受信記録 {}、取込 {}、欠け {}",
                    action,
                    receivedRecordId,
                    ingestionId,
                    e.getMessage());
            return;
        }
        if (auditRecords.existsByActionAndTarget(action, RECEIVED_RECORD, receivedRecordId)) {
            return;
        }
        auditRecords.append(auditRecord);
    }

    private static String reference(Source source) {
        return source == null ? null : source.reference();
    }

    private static String adoptedTail(String infoVersion, UUID ingestionId, UUID dataSourceId, UUID adoptedValueId) {
        return ";infoVersion=" + part(infoVersion, "infoVersion")
                + origin(ingestionId, dataSourceId)
                + ";adoptedValueId=" + part(adoptedValueId, "adoptedValueId");
    }

    /** 要約の出どころの部品（取込 ID と情報源 ID。採用・隔離・判断で共通。Bolt 39d2 で 1 か所にまとめた）。 */
    private static String origin(UUID ingestionId, UUID dataSourceId) {
        return ";ingestionId=" + part(ingestionId, "ingestionId") + ";dataSourceId="
                + part(dataSourceId, "dataSourceId");
    }

    /**
     * 要約の必須の部品。採用の要約に任意の部品はなく、隔離で任意なのは外部 ID・外部版・物理行だけ（{@link #orNone}・{@link #lines}）。
     * 欠けは要約に「null」と入れずに内容の欠けとして扱う（計画の「変更後の要約」）。
     */
    private static String part(Object value, String name) {
        if (value == null || value.toString().isBlank()) {
            throw new AuditRecord.MissingEventValueException(name);
        }
        return value.toString();
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
