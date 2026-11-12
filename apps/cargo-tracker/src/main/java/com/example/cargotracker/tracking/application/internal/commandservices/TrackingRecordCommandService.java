package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordLookup;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordScope;
import com.example.cargotracker.tracking.domain.events.TrackingPlacedUnderReview;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentTrackingRecordUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.MilestoneAdoption;
import com.example.cargotracker.tracking.domain.model.aggregates.MilestoneRegistration;
import com.example.cargotracker.tracking.domain.model.aggregates.MilestoneRegistrationRejected;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡記録の入力ポート（追跡管理者が使う。US-12）。トランザクションの境界になる。登録時刻は Clock から得る。
 * 登録・採用で追跡記録が確認中に入ったときだけ、保存の後に同じトランザクションで DE-18（{@link TrackingPlacedUnderReview}）を発行する（Bolt 39gc。前例は
 * DE-22 の TrackingStartEventHandler）。状態が採用になった集荷・引渡しでは、同じく保存の後に DE-09・DE-10 を発行する（Bolt 39g2。追跡の listener が
 * 予約の公開 API で予約の状態と輸送段階を進める）。保存が競合したら発行しない。
 * 読み出しの直後に担当範囲を判定し（{@link TrackingRecordScope}。Bolt 38g。US-16 AC4）、範囲の外なら何も書かずに範囲外の結果を返す。判定は
 * 発生時刻の判定・登録済みの判定・版の照合より先（範囲の外の追跡記録の実績や版を示さない）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class TrackingRecordCommandService {

    private final TrackingRecordRepository repository;
    private final Clock clock;
    private final TrackingRecordScope scope;
    private final ApplicationEventPublisher eventPublisher;

    public TrackingRecordCommandService(
            TrackingRecordRepository repository,
            Clock clock,
            TrackingRecordScope scope,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.clock = clock;
        this.scope = scope;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 出典と発生時刻を指定して主要実績を登録する（US-12 AC1・AC2）。同じ出典の実績が登録済みかを版の照合より先に確かめ、
     * 二重送信（画面を開いたときの版のまま）にも既存の実績を返す（T-INV-02）。
     */
    @Transactional
    public MilestoneRegistrationOutcome registerMilestone(
            RegisterMilestoneCommand command, AuthenticatedActor operator) {
        TrackingRecord trackingRecord;
        switch (scope.find(command.trackingNumber(), operator)) {
            case TrackingRecordLookup.Found(TrackingRecord found) -> trackingRecord = found;
            case TrackingRecordLookup.NotFound _ -> {
                return new MilestoneRegistrationOutcome.NotFound();
            }
            case TrackingRecordLookup.OutOfScope _ -> {
                return new MilestoneRegistrationOutcome.OutOfScope();
            }
        }
        MilestoneRegistration registration;
        try {
            registration = trackingRecord.registerMilestone(
                    command.kind(),
                    command.location(),
                    command.occurredAt(),
                    command.source(),
                    command.registrant(),
                    new UtcInstant(clock.instant()));
        } catch (MilestoneRegistrationRejected rejected) {
            return new MilestoneRegistrationOutcome.Rejected(rejected.reason());
        }
        if (registration.alreadyRegistered()) {
            return new MilestoneRegistrationOutcome.AlreadyRegistered(
                    registration.milestone().milestoneNo());
        }
        if (trackingRecord.aggregateVersion() != command.expectedVersion()) {
            return new MilestoneRegistrationOutcome.Conflict();
        }
        try {
            repository.update(registration.trackingRecord());
        } catch (ConcurrentTrackingRecordUpdateException _) {
            return new MilestoneRegistrationOutcome.Conflict();
        }
        registration.placedUnderReview().ifPresent(eventPublisher::publishEvent);
        registration.pickupAdopted().ifPresent(eventPublisher::publishEvent);
        registration.deliveryConfirmed().ifPresent(eventPublisher::publishEvent);
        return new MilestoneRegistrationOutcome.Registered(
                registration.milestone().milestoneNo(),
                registration.milestone().state(),
                registration.trackingRecord().currentStatus());
    }

    /**
     * 追跡管理者が下書きの候補を採用する（US-12 AC1・AC2 の外部の分。Bolt 39gb）。担当範囲を確かめた後、追跡番号の行を押さえて読み直し（DE-13 の受け取りと直列にする。
     * 決定 (5a)）、画面を開いたときの版と比べる。採用の後に新しい DE-13 が届いていれば、版の不一致で競合にする（候補が変わったかもしれないので読み直してもらう）。
     *
     * @param trackingNumber 追跡番号
     * @param milestoneNo 採用する実績の実績番号
     * @param expectedVersion 画面を開いたときの追跡記録の版
     * @param operator 操作者（採用者）
     * @return 採用の結果
     */
    @Transactional
    public MilestoneAdoptionOutcome adoptCandidate(
            TrackingNumber trackingNumber, int milestoneNo, long expectedVersion, AuthenticatedActor operator) {
        switch (scope.find(trackingNumber, operator)) {
            case TrackingRecordLookup.Found _ -> {
                // 範囲の中。ロックして読み直す
            }
            case TrackingRecordLookup.NotFound _ -> {
                return new MilestoneAdoptionOutcome.NotFound();
            }
            case TrackingRecordLookup.OutOfScope _ -> {
                return new MilestoneAdoptionOutcome.OutOfScope();
            }
        }
        TrackingRecord trackingRecord =
                repository.findByTrackingNumberForUpdate(trackingNumber).orElse(null);
        if (trackingRecord == null) {
            return new MilestoneAdoptionOutcome.NotFound();
        }
        if (trackingRecord.aggregateVersion() != expectedVersion) {
            return new MilestoneAdoptionOutcome.Conflict();
        }
        return switch (trackingRecord.adoptCandidate(milestoneNo, operator.userId(), new UtcInstant(clock.instant()))) {
            case MilestoneAdoption.Adopted adopted -> save(adopted);
            case MilestoneAdoption.SameAsAdopted(Milestone adopted) ->
                new MilestoneAdoptionOutcome.SameAsAdopted(adopted.milestoneNo());
            case MilestoneAdoption.Rejected(AdoptionRejectionReason reason, Optional<Milestone> decided) ->
                new MilestoneAdoptionOutcome.Rejected(reason, decided.map(Milestone::state));
        };
    }

    private MilestoneAdoptionOutcome save(MilestoneAdoption.Adopted adopted) {
        try {
            repository.update(adopted.trackingRecord());
        } catch (ConcurrentTrackingRecordUpdateException _) {
            return new MilestoneAdoptionOutcome.Conflict();
        }
        adopted.placedUnderReview().ifPresent(eventPublisher::publishEvent);
        adopted.pickupAdopted().ifPresent(eventPublisher::publishEvent);
        adopted.deliveryConfirmed().ifPresent(eventPublisher::publishEvent);
        return new MilestoneAdoptionOutcome.Adopted(
                adopted.milestone().milestoneNo(),
                adopted.milestone().state(),
                adopted.trackingRecord().currentStatus());
    }
}
