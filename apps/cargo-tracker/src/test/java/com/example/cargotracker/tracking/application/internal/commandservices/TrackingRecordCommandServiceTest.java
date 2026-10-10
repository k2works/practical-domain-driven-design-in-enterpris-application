package com.example.cargotracker.tracking.application.internal.commandservices;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.BOOKING_ID;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.CONSIGNEE;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.SHIPPER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.STARTED_AT;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentTrackingRecordUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 主要実績の登録の入力ポート（US-12 AC1・AC2。Bolt 26b）。 */
class TrackingRecordCommandServiceTest {

    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-000000026b01"));
    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

    private final InMemoryTrackingRecordRepository repository = new InMemoryTrackingRecordRepository();
    private final TrackingRecordCommandService service =
            new TrackingRecordCommandService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void 追跡を開始した追跡記録がある() {
        repository.save(TrackingRecord.start(TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                .trackingRecord());
    }

    @Test
    void 登録すると保存して登録した実績番号と導出し直した現在状態を返し版を1増やす() {
        MilestoneRegistrationOutcome outcome = service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"));

        assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.Registered(1, TrackingStatus.PICKED_UP));
        TrackingRecord saved = repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow();
        assertThat(saved.milestones()).hasSize(1);
        assertThat(saved.milestones().getFirst().registeredAt()).isEqualTo(new UtcInstant(NOW));
        assertThat(saved.currentStatus()).isEqualTo(TrackingStatus.PICKED_UP);
        assertThat(saved.aggregateVersion()).isEqualTo(1);
    }

    @Test
    void 同じ出典の2件目は既存の実績番号を返し保存しない() {
        service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"));

        MilestoneRegistrationOutcome outcome = service.registerMilestone(command(1, "F-118", "2026-11-01T02:30:00Z"));

        assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.AlreadyRegistered(1));
        TrackingRecord saved = repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow();
        assertThat(saved.milestones()).hasSize(1);
        assertThat(saved.aggregateVersion()).isEqualTo(1);
    }

    @Test
    void 画面を開いた後に別の登録が先に保存されていても同じ出典なら既存の実績番号を返す() {
        service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"));

        // 同じ画面からの二重送信（画面を開いたときの版 0 のまま）
        MilestoneRegistrationOutcome outcome = service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"));

        assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.AlreadyRegistered(1));
    }

    @Test
    void 画面を開いたときの版と違えば競合にして保存しない() {
        MilestoneRegistrationOutcome outcome = service.registerMilestone(command(7, "F-118", "2026-11-01T02:30:00Z"));

        assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.Conflict());
        assertThat(repository
                        .findByTrackingNumber(TRACKING_NUMBER)
                        .orElseThrow()
                        .milestones())
                .isEmpty();
    }

    @Test
    void 保存のときに楽観ロックの競合が起きたら競合にする() {
        TrackingRecordCommandService conflicting = new TrackingRecordCommandService(
                new InMemoryTrackingRecordRepository() {
                    @Override
                    public void update(TrackingRecord trackingRecord) {
                        throw new ConcurrentTrackingRecordUpdateException(
                                trackingRecord.trackingNumber(), trackingRecord.aggregateVersion());
                    }

                    {
                        save(TrackingRecord.start(
                                        TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                                .trackingRecord());
                    }
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(conflicting.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z")))
                .isEqualTo(new MilestoneRegistrationOutcome.Conflict());
    }

    @Test
    void 追跡番号の追跡記録がなければ見つからない() {
        RegisterMilestoneCommand unknown = new RegisterMilestoneCommand(
                new TrackingNumber("CTZZZZZZZZ2345"),
                0,
                MilestoneKind.PICKUP,
                new Location("JPTYO"),
                at("2026-11-01T02:30:00Z"),
                new Source(SourceKind.FIELD_RECORD, "F-118", new UtcInstant(NOW)),
                REGISTRANT);

        assertThat(service.registerMilestone(unknown)).isEqualTo(new MilestoneRegistrationOutcome.NotFound());
    }

    @Test
    void 発生時刻が登録時刻より後なら拒否の理由を返し保存しない() {
        MilestoneRegistrationOutcome outcome = service.registerMilestone(command(0, "F-118", "2026-11-01T03:00:01Z"));

        assertThat(outcome)
                .isEqualTo(new MilestoneRegistrationOutcome.Rejected(MilestoneRejectionReason.OCCURRED_IN_FUTURE));
        assertThat(repository
                        .findByTrackingNumber(TRACKING_NUMBER)
                        .orElseThrow()
                        .milestones())
                .isEmpty();
    }

    private static RegisterMilestoneCommand command(long expectedVersion, String reference, String occurredAt) {
        return new RegisterMilestoneCommand(
                TRACKING_NUMBER,
                expectedVersion,
                MilestoneKind.PICKUP,
                new Location("JPTYO"),
                at(occurredAt),
                new Source(SourceKind.FIELD_RECORD, reference, new UtcInstant(NOW)),
                REGISTRANT);
    }
}
