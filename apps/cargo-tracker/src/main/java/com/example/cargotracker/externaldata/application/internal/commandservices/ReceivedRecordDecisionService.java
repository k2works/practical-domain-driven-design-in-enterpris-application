package com.example.cargotracker.externaldata.application.internal.commandservices;

import com.example.cargotracker.externaldata.application.internal.commands.RejectReceivedRecordCommand;
import com.example.cargotracker.externaldata.domain.events.ReceivedRecordDecided;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateReceivedRecordDecisionException;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受信記録の判断のサービス。データ責任者が S-17 の判断の画面で隔離の受信記録を却下する（US-14 AC4、UC-15 3b。Bolt 39d2）。1 つの
 * トランザクションで、役割 → 受信記録（{@code findById}）→ 隔離か → 判断済みか（{@code existsFor}）の順に確かめ、判断を追記して DE-29 を出す。
 * 同時の判断に負けた一意制約の違反も判断済みとして返す（判断のリポジトリがセーブポイントに戻す）。情報源 ID は受信記録の取込から読む。判断の
 * 時刻は Clock から 1 回取る（判断の不変条件がマイクロ秒に切り捨てる）。判断できる受信記録の定め（{@link #refusalOf}）は判断の画面の
 * 行き先にも使い、GET と POST で 1 つにする（開始準備の決定 (3)）。
 */
@Service // JIG がユースケースとして読む印（組み立ては ExternalDataConfiguration）
public class ReceivedRecordDecisionService {

    private final ReceivedRecordRepository receivedRecords;
    private final ReceivedRecordDecisionRepository decisions;
    private final IngestionRepository ingestions;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReceivedRecordDecisionService(
            ReceivedRecordRepository receivedRecords,
            ReceivedRecordDecisionRepository decisions,
            IngestionRepository ingestions,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.receivedRecords = receivedRecords;
        this.decisions = decisions;
        this.ingestions = ingestions;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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
        try {
            decisions.add(decision);
        } catch (DuplicateReceivedRecordDecisionException _) {
            return new DecisionOutcome.AlreadyDecided();
        }
        // 受信記録は取込への外部キーを持ち、findById は取込で書いた受信記録だけを返すので、取込は必ずある
        Ingestion ingestion = ingestions.findById(receivedRecord.ingestionId()).orElseThrow();
        eventPublisher.publishEvent(new ReceivedRecordDecided(
                receivedRecord.id(),
                ingestion.id(),
                ingestion.dataSourceId(),
                decision.decision().name(),
                decision.reason(),
                actor.userId(),
                actor.companyId(),
                new UtcInstant(decision.decidedAt())));
        return new DecisionOutcome.Rejected(receivedRecord.id());
    }

    /**
     * 判断できる受信記録の定めのうち、読めた受信記録（取込で書いた受信記録）について、分類が隔離で判断がないかを確かめる。判断できれば空。
     */
    private Optional<DecisionOutcome> refusalOf(ReceivedRecord receivedRecord) {
        if (!ReceivedRecordDecision.decidable(receivedRecord)) {
            return Optional.of(new DecisionOutcome.NotDecidable());
        }
        if (decisions.existsFor(receivedRecord.id())) {
            return Optional.of(new DecisionOutcome.AlreadyDecided());
        }
        return Optional.empty();
    }
}
