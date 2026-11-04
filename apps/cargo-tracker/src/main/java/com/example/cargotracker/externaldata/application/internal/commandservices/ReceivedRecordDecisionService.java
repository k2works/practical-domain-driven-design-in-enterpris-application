package com.example.cargotracker.externaldata.application.internal.commandservices;

import com.example.cargotracker.externaldata.application.internal.commands.RejectKeylessQuarantinesCommand;
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
import java.time.Instant;
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
 * 行き先にも使い、GET と POST で 1 つにする（開始準備の決定 (3)）。1 つの取込の形式の誤りのまとめての却下（{@link #rejectKeyless}。
 * Bolt 39d2b）も受信記録ごとに同じ判断と DE-29 を 1 つずつ作る。
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
        if (!added(decision)) {
            return new DecisionOutcome.AlreadyDecided();
        }
        // 受信記録は取込への外部キーを持ち、findById は取込で書いた受信記録だけを返すので、取込は必ずある
        Ingestion ingestion = ingestions.findById(receivedRecord.ingestionId()).orElseThrow();
        publishDecided(decision, ingestion, actor);
        return new DecisionOutcome.Rejected(receivedRecord.id());
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

    /** 判断 1 つに DE-29 を 1 つ出す（1 件ずつの却下とまとめての却下で同じ形。情報源 ID は取込から読む）。 */
    private void publishDecided(ReceivedRecordDecision decision, Ingestion ingestion, AuthenticatedActor actor) {
        eventPublisher.publishEvent(new ReceivedRecordDecided(
                decision.receivedRecordId(),
                ingestion.id(),
                ingestion.dataSourceId(),
                decision.decision().name(),
                decision.reason(),
                actor.userId(),
                actor.companyId(),
                new UtcInstant(decision.decidedAt())));
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
