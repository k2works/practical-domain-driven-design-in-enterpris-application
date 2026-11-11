package com.example.cargotracker.tracking.application.internal.commandservices;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.BOOKING_ID;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.CONSIGNEE;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.SHIPPER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.STARTED_AT;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordScope;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentTrackingRecordUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.ExternalMilestoneReceipt;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
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
    /** 追跡記録の荷主企業を担当する追跡管理者（Bolt 38g）。 */
    private static final AuthenticatedActor MANAGER =
            AuthenticatedActorFixture.trackingManagerHandling(REGISTRANT, SHIPPER);
    /** 担当企業の外の追跡管理者（Bolt 38g）。 */
    private static final AuthenticatedActor OUTSIDER =
            AuthenticatedActorFixture.trackingManagerHandling(REGISTRANT, new CompanyId(UUID.randomUUID()));

    private final TrackingRecordCommandService service = new TrackingRecordCommandService(
            repository, Clock.fixed(NOW, ZoneOffset.UTC), new TrackingRecordScope(repository));

    @BeforeEach
    void 追跡を開始した追跡記録がある() {
        repository.save(TrackingRecord.start(TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                .trackingRecord());
    }

    @Test
    void 登録すると保存して登録した実績番号と導出し直した現在状態を返し版を1増やす() {
        MilestoneRegistrationOutcome outcome =
                service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"), MANAGER);

        assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.Registered(1, TrackingStatus.PICKED_UP));
        TrackingRecord saved = repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow();
        assertThat(saved.milestones()).hasSize(1);
        assertThat(saved.milestones().getFirst().registeredAt()).isEqualTo(new UtcInstant(NOW));
        assertThat(saved.currentStatus()).isEqualTo(TrackingStatus.PICKED_UP);
        assertThat(saved.aggregateVersion()).isEqualTo(1);
    }

    @Test
    void 同じ出典の2件目は既存の実績番号を返し保存しない() {
        service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"), MANAGER);

        MilestoneRegistrationOutcome outcome =
                service.registerMilestone(command(1, "F-118", "2026-11-01T02:30:00Z"), MANAGER);

        assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.AlreadyRegistered(1));
        TrackingRecord saved = repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow();
        assertThat(saved.milestones()).hasSize(1);
        assertThat(saved.aggregateVersion()).isEqualTo(1);
    }

    @Test
    void 画面を開いた後に別の登録が先に保存されていても同じ出典なら既存の実績番号を返す() {
        service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"), MANAGER);

        // 同じ画面からの二重送信（画面を開いたときの版 0 のまま）
        MilestoneRegistrationOutcome outcome =
                service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"), MANAGER);

        assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.AlreadyRegistered(1));
    }

    @Test
    void 画面を開いたときの版と違えば競合にして保存しない() {
        MilestoneRegistrationOutcome outcome =
                service.registerMilestone(command(7, "F-118", "2026-11-01T02:30:00Z"), MANAGER);

        assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.Conflict());
        assertThat(repository
                        .findByTrackingNumber(TRACKING_NUMBER)
                        .orElseThrow()
                        .milestones())
                .isEmpty();
    }

    @Test
    void 保存のときに楽観ロックの競合が起きたら競合にする() {
        InMemoryTrackingRecordRepository failing = new InMemoryTrackingRecordRepository() {
            @Override
            public void update(TrackingRecord trackingRecord) {
                throw new ConcurrentTrackingRecordUpdateException(
                        trackingRecord.trackingNumber(), trackingRecord.aggregateVersion());
            }

            {
                save(TrackingRecord.start(TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                        .trackingRecord());
            }
        };
        TrackingRecordCommandService conflicting = new TrackingRecordCommandService(
                failing, Clock.fixed(NOW, ZoneOffset.UTC), new TrackingRecordScope(failing));

        assertThat(conflicting.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"), MANAGER))
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

        assertThat(service.registerMilestone(unknown, MANAGER)).isEqualTo(new MilestoneRegistrationOutcome.NotFound());
    }

    @Test
    void 発生時刻が登録時刻より後なら拒否の理由を返し保存しない() {
        MilestoneRegistrationOutcome outcome =
                service.registerMilestone(command(0, "F-118", "2026-11-01T03:00:01Z"), MANAGER);

        assertThat(outcome)
                .isEqualTo(new MilestoneRegistrationOutcome.Rejected(MilestoneRejectionReason.OCCURRED_IN_FUTURE));
        assertThat(repository
                        .findByTrackingNumber(TRACKING_NUMBER)
                        .orElseThrow()
                        .milestones())
                .isEmpty();
    }

    @Test
    void 画面を開いたときの版と違っても発生時刻が未来なら拒否の理由を返す() {
        MilestoneRegistrationOutcome outcome =
                service.registerMilestone(command(7, "F-118", "2026-11-01T03:00:01Z"), MANAGER);

        assertThat(outcome)
                .isEqualTo(new MilestoneRegistrationOutcome.Rejected(MilestoneRejectionReason.OCCURRED_IN_FUTURE));
    }

    @Test
    void 範囲の外の追跡記録には登録せずに範囲外を返す() {
        // 行 5c（画面を開いた後に割り当てが外れた）
        assertThat(service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"), OUTSIDER))
                .isEqualTo(new MilestoneRegistrationOutcome.OutOfScope());
        assertThat(repository
                        .findByTrackingNumber(TRACKING_NUMBER)
                        .orElseThrow()
                        .milestones())
                .isEmpty();
    }

    @Test
    void 範囲の外では登録済みも競合も未来の発生時刻も示さず範囲外を返す() {
        // 行 5d（範囲の判定が、登録済み・版の照合・発生時刻の判定より先。存在を漏らさない）
        service.registerMilestone(command(0, "F-118", "2026-11-01T02:30:00Z"), MANAGER);

        assertThat(service.registerMilestone(command(1, "F-118", "2026-11-01T02:30:00Z"), OUTSIDER))
                .isEqualTo(new MilestoneRegistrationOutcome.OutOfScope());
        assertThat(service.registerMilestone(command(0, "F-119", "2026-11-01T02:30:00Z"), OUTSIDER))
                .isEqualTo(new MilestoneRegistrationOutcome.OutOfScope());
        assertThat(service.registerMilestone(command(1, "F-120", "2026-11-01T03:00:01Z"), OUTSIDER))
                .isEqualTo(new MilestoneRegistrationOutcome.OutOfScope());
    }

    /** Bolt 39gb の行 50〜53・57（候補の採用の入力ポート）。 */
    @org.junit.jupiter.api.Nested
    class Bolt39gb_候補の採用 {

        private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-0000039b0d01");

        /** DE-13 の下書きを届ける（版が 1 上がる）。 */
        private void arrive(String reference, String location, String publishedAt) {
            TrackingRecord current = repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow();
            ExternalMilestone external = new ExternalMilestone(
                    TRACKING_NUMBER,
                    MilestoneKind.DEPARTURE,
                    new Location(location),
                    at("2026-11-01T01:00:00Z"),
                    new Source(SourceKind.EXTERNAL_RECORD, reference, at("2026-11-01T01:30:00Z")),
                    new ExternalOrigin(SOURCE_ID, "MS-0001", at(publishedAt)),
                    REGISTRANT);
            repository.update(((ExternalMilestoneReceipt.Received)
                            current.receiveExternalMilestone(external, at(publishedAt)))
                    .trackingRecord());
        }

        @Test
        void 行50_候補を採用すると保存して実績番号と導出し直した現在状態を返し版を1増やす() {
            arrive("r-1", "JPTYO", "2026-11-01T02:00:00Z");

            MilestoneAdoptionOutcome outcome = service.adoptCandidate(TRACKING_NUMBER, 1, 1, MANAGER);

            assertThat(outcome).isEqualTo(new MilestoneAdoptionOutcome.Adopted(1, TrackingStatus.IN_TRANSIT));
            TrackingRecord saved = repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow();
            assertThat(saved.milestones()).hasSize(1);
            assertThat(saved.milestones().getFirst().state()).isEqualTo(MilestoneState.ADOPTED);
            assertThat(saved.milestones().getFirst().adoptedBy()).isEqualTo(REGISTRANT);
            assertThat(saved.milestones().getFirst().adoptedAt()).isEqualTo(new UtcInstant(NOW));
            assertThat(saved.aggregateVersion()).isEqualTo(2);
        }

        @Test
        void 行57_画面を開いた後に新しいDE13が届いていたら競合にして保存しない() {
            arrive("r-1", "JPTYO", "2026-11-01T02:00:00Z");
            // 画面を開いたときの版は 1。その後に新しい原本が届き、候補が変わった
            arrive("r-2", "KRPUS", "2026-11-01T02:30:00Z");

            assertThat(service.adoptCandidate(TRACKING_NUMBER, 1, 1, MANAGER))
                    .isEqualTo(new MilestoneAdoptionOutcome.Conflict());
            assertThat(service.adoptCandidate(TRACKING_NUMBER, 2, 1, MANAGER))
                    .isEqualTo(new MilestoneAdoptionOutcome.Conflict());
            assertThat(repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow().milestones())
                    .allSatisfy(milestone -> assertThat(milestone.state()).isEqualTo(MilestoneState.DRAFT));
        }

        @Test
        void 行57_保存のときに楽観ロックの競合が起きたら競合にする() {
            InMemoryTrackingRecordRepository failing = new InMemoryTrackingRecordRepository() {
                @Override
                public void update(TrackingRecord trackingRecord) {
                    throw new ConcurrentTrackingRecordUpdateException(
                            trackingRecord.trackingNumber(), trackingRecord.aggregateVersion());
                }
            };
            TrackingRecord started = TrackingRecord.start(
                            TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                    .trackingRecord();
            ExternalMilestone external = new ExternalMilestone(
                    TRACKING_NUMBER,
                    MilestoneKind.DEPARTURE,
                    new Location("JPTYO"),
                    at("2026-11-01T01:00:00Z"),
                    new Source(SourceKind.EXTERNAL_RECORD, "r-1", at("2026-11-01T01:30:00Z")),
                    new ExternalOrigin(SOURCE_ID, "MS-0001", at("2026-11-01T02:00:00Z")),
                    REGISTRANT);
            failing.save(((ExternalMilestoneReceipt.Received)
                            started.receiveExternalMilestone(external, at("2026-11-01T02:00:00Z")))
                    .trackingRecord());
            TrackingRecordCommandService failingService = new TrackingRecordCommandService(
                    failing, Clock.fixed(NOW, ZoneOffset.UTC), new TrackingRecordScope(failing));

            assertThat(failingService.adoptCandidate(TRACKING_NUMBER, 1, 0, MANAGER))
                    .isEqualTo(new MilestoneAdoptionOutcome.Conflict());
        }

        @Test
        void 行51_候補でない下書きは拒否して保存しない() {
            arrive("r-1", "JPTYO", "2026-11-01T02:00:00Z");
            arrive("r-2", "KRPUS", "2026-11-01T02:30:00Z");

            assertThat(service.adoptCandidate(TRACKING_NUMBER, 1, 2, MANAGER))
                    .isEqualTo(new MilestoneAdoptionOutcome.Rejected(AdoptionRejectionReason.NOT_CANDIDATE));
            assertThat(repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow().aggregateVersion())
                    .isEqualTo(2);
        }

        @Test
        void 行53_採用済みと同じ値の候補は何もせず採用済みの実績番号を返す() {
            arrive("r-1", "JPTYO", "2026-11-01T02:00:00Z");
            service.adoptCandidate(TRACKING_NUMBER, 1, 1, MANAGER);
            arrive("r-2", "JPTYO", "2026-11-01T02:30:00Z");

            assertThat(service.adoptCandidate(TRACKING_NUMBER, 2, 3, MANAGER))
                    .isEqualTo(new MilestoneAdoptionOutcome.SameAsAdopted(1));
            assertThat(repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow().aggregateVersion())
                    .isEqualTo(3);
        }

        @Test
        void ない追跡番号と範囲の外() {
            assertThat(service.adoptCandidate(new TrackingNumber("CTZZZZZZZZZZZZ"), 1, 0, MANAGER))
                    .isEqualTo(new MilestoneAdoptionOutcome.NotFound());
            arrive("r-1", "JPTYO", "2026-11-01T02:00:00Z");
            assertThat(service.adoptCandidate(TRACKING_NUMBER, 1, 1, OUTSIDER))
                    .isEqualTo(new MilestoneAdoptionOutcome.OutOfScope());
        }
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
