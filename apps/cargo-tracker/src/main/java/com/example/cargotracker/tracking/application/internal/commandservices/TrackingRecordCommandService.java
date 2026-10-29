package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordLookup;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordScope;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentTrackingRecordUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.MilestoneRegistration;
import com.example.cargotracker.tracking.domain.model.aggregates.MilestoneRegistrationRejected;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡記録の入力ポート（追跡管理者が使う。US-12）。トランザクションの境界になる。登録時刻は Clock から得る。
 * ドメインイベントは発行しない（DE-09 集荷実績を採用した は W7。Bolt 26b の確認ポイント 10）。
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

    public TrackingRecordCommandService(TrackingRecordRepository repository, Clock clock, TrackingRecordScope scope) {
        this.repository = repository;
        this.clock = clock;
        this.scope = scope;
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
        return new MilestoneRegistrationOutcome.Registered(
                registration.milestone().milestoneNo(),
                registration.trackingRecord().currentStatus());
    }
}
