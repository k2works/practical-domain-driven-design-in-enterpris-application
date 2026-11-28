package com.example.cargotracker.externaldata.application.internal.commandservices;

import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectKeylessQuarantinesCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectReceivedRecordCommand;
import com.example.cargotracker.externaldata.domain.events.ReceivedRecordDecided;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateReceivedRecordDecisionException;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.rules.DiscrepancyRule;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ExternalIdAdoptedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ReceivedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受信記録の判断のサービス。データ責任者が S-17 の判断の画面で隔離の受信記録を却下する（US-14 AC4、UC-15 3b。Bolt 39d2）か、内容の違う原本の
 * 隔離を採用する（{@link #adopt}。UC-15 の主成功シナリオ 5。航海は Bolt 39d3b、主要実績は Bolt 39d3b2）。却下は、1 つの
 * トランザクションで、役割 → 受信記録（{@code findById}）→ 隔離か → 判断済みか（{@code existsFor}）の順に確かめ、判断を追記して DE-29 を出す。
 * 同時の判断に負けた一意制約の違反も判断済みとして返す（判断のリポジトリがセーブポイントに戻す）。情報源 ID は受信記録の取込から読む。判断の
 * 時刻は Clock から 1 回取る（判断の不変条件がマイクロ秒に切り捨てる）。判断できる受信記録の定め（{@link #refusalOf}）は判断の画面の
 * 行き先にも使い、GET と POST で 1 つにする（開始準備の決定 (3)）。1 つの取込の形式の誤りのまとめての却下（{@link #rejectKeyless}。
 * Bolt 39d2b）も受信記録ごとに同じ判断と DE-29 を 1 つずつ作る。
 */
@Service // JIG がユースケースとして読む印（組み立ては ExternalDataConfiguration）
public class ReceivedRecordDecisionService {

    private final ReceivedRecordRepository receivedRecords;
    private final ReceivedRecordDecisionRepository decisions;
    private final IngestionRepository ingestions;
    private final DataSourceRepository dataSources;
    private final ExternalRecordRepository externalRecords;
    private final AdoptedValueRepository adoptedValues;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @SuppressWarnings("java:S107") // 組み立ての入口（判断と採用が読み書きするポートとイベントと時計）。前例 IngestionService
    public ReceivedRecordDecisionService(
            ReceivedRecordRepository receivedRecords,
            ReceivedRecordDecisionRepository decisions,
            IngestionRepository ingestions,
            DataSourceRepository dataSources,
            ExternalRecordRepository externalRecords,
            AdoptedValueRepository adoptedValues,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.receivedRecords = receivedRecords;
        this.decisions = decisions;
        this.ingestions = ingestions;
        this.dataSources = dataSources;
        this.externalRecords = externalRecords;
        this.adoptedValues = adoptedValues;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 判断の画面の採用の欄の出し分け（Bolt 39d3b の開始準備の決定 (4)）。採用できる隔離なら空、できなければ断る結果（{@link DecisionOutcome.NotFound}・
     * {@link DecisionOutcome.NotDecidable}・{@link DecisionOutcome.AlreadyDecided}・{@link DecisionOutcome.NotAdoptable}）。{@link #adopt} と同じ定めを
     * 使い、画面に欄が出る隔離と POST で採用できる隔離を食い違わせない。
     */
    @Transactional(readOnly = true)
    public Optional<DecisionOutcome> adoptionRefusalOf(UUID receivedRecordId) {
        Optional<ReceivedRecord> found = receivedRecords.findById(receivedRecordId);
        if (found.isEmpty()) {
            return Optional.of(new DecisionOutcome.NotFound());
        }
        Optional<DecisionOutcome> refusal = refusalOf(found.get());
        if (refusal.isPresent()) {
            return refusal;
        }
        DecisionOutcome.NotAdoptableReason reason = adoptionCheckOf(found.get()).refusal();
        return reason == null
                ? Optional.empty()
                : Optional.of(new DecisionOutcome.NotAdoptable(receivedRecordId, reason));
    }

    /**
     * 内容の違う原本の隔離を採用する（航海は Bolt 39d3b の開始準備の決定 (4)〜(6)、主要実績は Bolt 39d3b2）。1 つのトランザクションで、役割 → 受信記録 →
     * 隔離か → 判断済みか → 採用できるかの順に確かめ、判断をセーブポイントの中で先に追記し、同時の判断に負けなければ採用値をセーブポイントなしで追記して、
     * DE-29・採用値のイベント（航海は DE-12、主要実績は DE-13）の順に出す。負けた側は採用値を書かず判断済みを返す（PostgreSQL は制約違反でトランザクションを
     * 中断するので、判断を先に書く）。採用値の一意制約の違反は判断の一意制約を通った後には起きない経路なので捕まえずに上げ、トランザクションごと巻き戻す。
     * 判断の時刻は Clock から 1 回取り、採用値と採用値のイベントの公開した時刻も同じ値にする。採用値の後に外部原本の基準を採用した原本に付け替える（Bolt 39d4、【業務の判断】(b)）。読んだ後に
     * ほかの採用が先に基準を付け替えていれば {@link com.example.cargotracker.externaldata.domain.model.aggregates.ConcurrentExternalRecordUpdateException} を捕まえずに上げ、
     * トランザクションごと巻き戻す（判断・採用値・イベントの発行記録を残さない）。
     *
     * @param actor 操作者（データ責任者）
     * @param command 採用のコマンド
     */
    @Transactional
    public DecisionOutcome adopt(AuthenticatedActor actor, AdoptReceivedRecordCommand command) {
        if (!actor.hasRole(Role.DATA_STEWARD)) {
            return new DecisionOutcome.NotPermitted();
        }
        Optional<ReceivedRecord> found = receivedRecords.findById(command.receivedRecordId());
        if (found.isEmpty()) {
            return new DecisionOutcome.NotFound();
        }
        ReceivedRecord receivedRecord = found.get();
        Optional<DecisionOutcome> refusal = refusalOf(receivedRecord);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        AdoptionCheck check = adoptionCheckOf(receivedRecord);
        if (check.refusal() != null) {
            return new DecisionOutcome.NotAdoptable(receivedRecord.id(), check.refusal());
        }
        AdoptionParts parts = check.parts();
        // 版の逆転は、採用値を足す前に判定する（足した後は、この採用値が最後に取得した採用値になる。Bolt 40-2）
        VersionReversal reversal =
                reversalOf(receivedRecord, parts.externalRecord().key());
        ReceivedRecordDecision decision =
                ReceivedRecordDecision.adopt(receivedRecord, command.reason(), actor.userId(), clock.instant());
        if (!added(decision)) {
            return new DecisionOutcome.AlreadyDecided(receivedRecord.classification());
        }
        ExternalRecord externalRecord = parts.externalRecord();
        AdoptionFactory.Adoption adoption = AdoptionFactory.adopt(
                receivedRecord,
                externalRecord.key(),
                parts.values(),
                parts.ingestion().dataSourceId(),
                actor,
                decision.decidedAt());
        adoptedValues.add(adoption.adoptedValue());
        externalRecords.changeAdoptedPayload(
                externalRecord.id(),
                externalRecord.payloadHash(),
                receivedRecord.payloadHash(),
                receivedRecord.payloadObjectKey());
        publishDecided(decision, parts.ingestion(), actor, reversal);
        eventPublisher.publishEvent(adoption.event());
        return new DecisionOutcome.AdoptedQuarantine(receivedRecord.id());
    }

    /**
     * 採用できる隔離の定めと、採用に要るものか断る理由（判断できる隔離について呼ぶ。GET と POST で 1 つの定め）。外部原本を持ち（【業務の判断】(a)）、受け取った
     * 値の列があり、情報源が判断の時点で承認済みで（【業務の判断】(n)・(o)）、同じ原本がすでに採用されておらず（基準と同じハッシュでない。【業務の判断】(p)）、受け取った値を
     * 読め（【業務の判断】(k)）、主要実績は追跡番号がこの外部版の採用値と同じ（【業務の判断】(m)）ときだけ採用できる。この順に確かめ、先の理由で片付く隔離で後の照会を引かない。原本種別は定めに置かず、値を読めるかで決まる
     * （知らない原本種別は読めない）。読めない値の WARN は判断の画面の照会のサービスが書く。
     */
    private AdoptionCheck adoptionCheckOf(ReceivedRecord receivedRecord) {
        if (receivedRecord.classification() == Classification.DISCREPANCY) {
            // 差異はどの状態でも採用できない。最初の差異は外部原本の基準と同じハッシュなので、分類を最初に確かめる（Bolt 39h2 の決定 (3)）
            return AdoptionCheck.refused(DecisionOutcome.NotAdoptableReason.DISCREPANCY);
        }
        ReceivedValue value = receivedRecord.value();
        if (receivedRecord.externalRecordId() == null) {
            return AdoptionCheck.refused(DecisionOutcome.NotAdoptableReason.KEYLESS);
        }
        if (value == null) {
            return AdoptionCheck.refused(DecisionOutcome.NotAdoptableReason.VALUE_NOT_RECORDED);
        }
        // 受信記録は取込と外部原本への、取込は情報源への外部キーを持つので、どれも必ずある
        Ingestion ingestion = ingestions.findById(receivedRecord.ingestionId()).orElseThrow();
        DataSource dataSource = dataSources.findById(ingestion.dataSourceId()).orElseThrow();
        if (dataSource.approvalStatus() != DataSource.ApprovalStatus.APPROVED) {
            return AdoptionCheck.refused(DecisionOutcome.NotAdoptableReason.SOURCE_NOT_APPROVED);
        }
        ExternalRecord externalRecord =
                externalRecords.findById(receivedRecord.externalRecordId()).orElseThrow();
        if (receivedRecord.payloadHash().equals(externalRecord.payloadHash())) {
            return AdoptionCheck.refused(DecisionOutcome.NotAdoptableReason.PAYLOAD_ALREADY_ADOPTED);
        }
        Optional<RecordValues> values = valuesOf(externalRecord.key(), value);
        if (values.isEmpty()) {
            return AdoptionCheck.refused(DecisionOutcome.NotAdoptableReason.VALUE_UNREADABLE);
        }
        if (trackingNumberDiffers(receivedRecord, values.get())) {
            return AdoptionCheck.refused(DecisionOutcome.NotAdoptableReason.TRACKING_NUMBER_DIFFERS);
        }
        return new AdoptionCheck(new AdoptionParts(ingestion, externalRecord, values.get()), null);
    }

    /**
     * 主要実績の受け取った値の追跡番号が、この外部版の採用値の対象のキーと違うか（【業務の判断】(m)）。採用値の要約は読まず（【業務の判断】(l)）、対象のキーの
     * 文字列を比べる。採用値がなければ比べない。航海は航海番号が外部 ID の列の値で同じ外部 ID なら同じなので、採用値を引かない。
     */
    private boolean trackingNumberDiffers(ReceivedRecord receivedRecord, RecordValues values) {
        return values instanceof MilestoneValues
                && adoptedValues
                        .currentOf(receivedRecord.externalRecordId())
                        .filter(current -> !current.targetKey().equals(values.targetKey()))
                        .isPresent();
    }

    /** 受け取った値を読む（読めなければ空。入口の {@link IllegalArgumentException} だけを受ける）。 */
    private static Optional<RecordValues> valuesOf(IdempotencyKey key, ReceivedValue value) {
        try {
            return Optional.of(RecordValues.ofSummary(key.recordType(), value.targetKey(), value.summary()));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    /**
     * 判断の画面の行き先。判断できる受信記録なら空、できなければ断る結果（{@link DecisionOutcome.NotFound}・
     * {@link DecisionOutcome.NotDecidable}・{@link DecisionOutcome.AlreadyDecided}）。
     */
    @Transactional(readOnly = true)
    public Optional<DecisionOutcome> refusalOf(UUID receivedRecordId) {
        return receivedRecords
                .findById(receivedRecordId)
                .map(this::refusalOf)
                .orElseGet(() -> Optional.of(new DecisionOutcome.NotFound()));
    }

    /**
     * 隔離を却下する。
     *
     * @param actor 操作者（データ責任者）
     * @param command 却下のコマンド
     */
    @Transactional
    public DecisionOutcome reject(AuthenticatedActor actor, RejectReceivedRecordCommand command) {
        if (!actor.hasRole(Role.DATA_STEWARD)) {
            return new DecisionOutcome.NotPermitted();
        }
        Optional<ReceivedRecord> found = receivedRecords.findById(command.receivedRecordId());
        if (found.isEmpty()) {
            return new DecisionOutcome.NotFound();
        }
        ReceivedRecord receivedRecord = found.get();
        Optional<DecisionOutcome> refusal = refusalOf(receivedRecord);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        ReceivedRecordDecision decision =
                ReceivedRecordDecision.reject(receivedRecord, command.reason(), actor.userId(), clock.instant());
        if (!added(decision)) {
            return new DecisionOutcome.AlreadyDecided(receivedRecord.classification());
        }
        // 受信記録は取込への外部キーを持ち、findById は取込で書いた受信記録だけを返すので、取込は必ずある
        Ingestion ingestion = ingestions.findById(receivedRecord.ingestionId()).orElseThrow();
        publishDecided(decision, ingestion, actor);
        return new DecisionOutcome.Rejected(receivedRecord.id(), receivedRecord.classification());
    }

    /**
     * 1 つの取込の形式の誤り（判断を待つキーのない隔離）をまとめて却下する（Bolt 39d2b、【業務の判断】(g)・(h)）。1 つのトランザクションで、
     * 役割 → 取込（{@code findById}。情報源 ID もここから）→ 対象（{@code undecidedKeylessQuarantinesOf}）の順に確かめ、判断の時刻を Clock
     * から 1 回取って、受信記録ごとに 1 件ずつの却下と同じ判断（{@link ReceivedRecordDecision#reject}）を追記し DE-29 を 1 つずつ出す。読んだ後に
     * ほかの人が 1 件ずつ却下した受信記録の一意制約の違反は、判断のリポジトリがセーブポイントに戻すので、とばして数えない（DE-29 も出さない）。
     * ほかの例外は握りつぶさず上げ、トランザクションごと巻き戻す（判断も DE-29 も残らない）。
     *
     * @param actor 操作者（データ責任者）
     * @param command まとめての却下のコマンド
     */
    @Transactional
    public KeylessRejectionOutcome rejectKeyless(AuthenticatedActor actor, RejectKeylessQuarantinesCommand command) {
        if (!actor.hasRole(Role.DATA_STEWARD)) {
            return new KeylessRejectionOutcome.NotPermitted();
        }
        Optional<Ingestion> found = ingestions.findById(command.ingestionId());
        if (found.isEmpty()) {
            return new KeylessRejectionOutcome.NotFound();
        }
        Ingestion ingestion = found.get();
        Instant decidedAt = clock.instant();
        int rejected = 0;
        for (ReceivedRecord target : receivedRecords.undecidedKeylessQuarantinesOf(ingestion.id())) {
            ReceivedRecordDecision decision =
                    ReceivedRecordDecision.reject(target, command.reason(), actor.userId(), decidedAt);
            if (added(decision)) {
                publishDecided(decision, ingestion, actor);
                rejected++;
            }
        }
        return rejected == 0
                ? new KeylessRejectionOutcome.NothingToReject()
                : new KeylessRejectionOutcome.RejectedAll(rejected);
    }

    /**
     * 判断を追記する。同時の判断に負けた一意制約の違反は追記しなかったとして偽を返す（判断のリポジトリがセーブポイントに戻す）。ほかの例外は
     * そのまま上げる。
     */
    private boolean added(ReceivedRecordDecision decision) {
        try {
            decisions.add(decision);
            return true;
        } catch (DuplicateReceivedRecordDecisionException _) {
            return false;
        }
    }

    /** 判断 1 つに DE-29 を 1 つ出す（1 件ずつの却下とまとめての却下で同じ形。情報源 ID は取込から読む）。却下は版の逆転でない。 */
    private void publishDecided(ReceivedRecordDecision decision, Ingestion ingestion, AuthenticatedActor actor) {
        publishDecided(decision, ingestion, actor, VersionReversal.NONE);
    }

    private void publishDecided(
            ReceivedRecordDecision decision, Ingestion ingestion, AuthenticatedActor actor, VersionReversal reversal) {
        eventPublisher.publishEvent(new ReceivedRecordDecided(
                decision.receivedRecordId(),
                ingestion.id(),
                ingestion.dataSourceId(),
                decision.decision().name(),
                decision.reason(),
                actor.userId(),
                actor.companyId(),
                new UtcInstant(decision.decidedAt()),
                reversal.reversed(),
                reversal.comparedExternalVersion()));
    }

    /**
     * 採用する隔離が版の逆転か（この外部 ID の最後に取得した採用値より前に取得した値か。差異の規則と同じ定義。D-142）。逆転なら比べた採用値の外部版を持つ。
     * 採用は止めず、DE-29 に残すだけ（ED-INV-03 を人が上書きしたことを監査に残す。Bolt 40-2）。
     */
    private VersionReversal reversalOf(ReceivedRecord receivedRecord, IdempotencyKey key) {
        java.util.List<ExternalIdAdoptedValue> sameExternalId =
                adoptedValues.findByExternalId(key.provider(), key.recordType(), key.externalId());
        if (!DiscrepancyRule.isVersionReversed(receivedRecord.acquiredAt(), sameExternalId)) {
            return VersionReversal.NONE;
        }
        return new VersionReversal(
                true,
                DiscrepancyRule.partnerOf(receivedRecord.acquiredAt(), sameExternalId)
                        .map(ExternalIdAdoptedValue::externalVersion)
                        .orElseThrow());
    }

    /** 採用が版の逆転か、逆転なら比べた採用値の外部版。 */
    private record VersionReversal(boolean reversed, String comparedExternalVersion) {
        private static final VersionReversal NONE = new VersionReversal(false, null);
    }

    /**
     * 判断できる受信記録の定めのうち、読めた受信記録（取込で書いた受信記録）について、分類が隔離で判断がないかを確かめる。判断できれば空。
     */
    private Optional<DecisionOutcome> refusalOf(ReceivedRecord receivedRecord) {
        if (!ReceivedRecordDecision.decidable(receivedRecord)) {
            return Optional.of(new DecisionOutcome.NotDecidable());
        }
        if (decisions.existsFor(receivedRecord.id())) {
            return Optional.of(new DecisionOutcome.AlreadyDecided(receivedRecord.classification()));
        }
        return Optional.empty();
    }

    /**
     * 採用に要るもの（採用できる隔離の定めを通った隔離について読んだ値）。
     *
     * @param ingestion 受信記録の取込（情報源 ID を読む）
     * @param externalRecord 受信記録の外部原本（定めの中で読んだ基準のハッシュを付け替えの条件にする。Bolt 39d4 の開始準備の決定 (3)）
     * @param values 受け取った値
     */
    private record AdoptionParts(Ingestion ingestion, ExternalRecord externalRecord, RecordValues values) {}

    /**
     * 採用できる隔離の定めの結果（どちらか一方が {@code null} でない。Bolt 39d3b2 の開始準備の決定 (3)）。
     *
     * @param parts 採用に要るもの（採用できないときは {@code null}）
     * @param refusal 採用できない理由（採用できるときは {@code null}）
     */
    private record AdoptionCheck(AdoptionParts parts, DecisionOutcome.NotAdoptableReason refusal) {

        private static AdoptionCheck refused(DecisionOutcome.NotAdoptableReason reason) {
            return new AdoptionCheck(null, reason);
        }
    }
}
