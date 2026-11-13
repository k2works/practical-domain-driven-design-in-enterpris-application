package com.example.cargotracker.tracking.application.internal.eventhandlers;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.BOOKING_ID;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.CONSIGNEE;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.SHIPPER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.STARTED_AT;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryRejectedMilestoneRepository;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

/** DE-13 を受けて追跡の実績か拒否の記録を書く listener（Bolt 39g の行 17・20〜23。単体。ポートはメモリの実装と呼び出しの記録）。 */
class ExternalMilestoneEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");
    private static final UtcInstant OCCURRED_AT = at("2026-11-19T14:00:00Z");
    private static final UtcInstant ACQUIRED_AT = at("2026-11-19T14:30:00Z");
    private static final UtcInstant PUBLISHED_AT = at("2026-11-19T15:00:00Z");
    private static final UUID DATA_SOURCE = UUID.fromString("00000000-0000-0000-0000-000000003911");
    private static final UserId STEWARD = new UserId(UUID.fromString("00000000-0000-0000-0000-000000003912"));
    private static final CompanyId OPERATOR_COMPANY =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000003913"));
    private static final Location TOKYO = new Location("JPTYO");
    private static final String RECEIVED_RECORD = "11111111-1111-1111-1111-111111111111";
    private static final String UNKNOWN_NUMBER = "CTZZZZZZZZZZZZ";
    private static final Source SOURCE = new Source(SourceKind.EXTERNAL_RECORD, RECEIVED_RECORD, ACQUIRED_AT);

    private final RecordingRepository repository = new RecordingRepository();
    private final InMemoryRejectedMilestoneRepository rejected = new InMemoryRejectedMilestoneRepository();
    /** 有人例外案件の起票の呼び出し（Bolt 40-13）。「理由/追跡記録あり/操作者」で記録する。 */
    private final List<String> opened = new ArrayList<>();

    private RuntimeException openFailure;

    private final ExternalMilestoneEventHandler handler = new ExternalMilestoneEventHandler(
            repository, rejected, Clock.fixed(NOW, ZoneOffset.UTC), (rejectedMilestone, found, operator) -> {
                if (openFailure != null) {
                    throw openFailure;
                }
                opened.add(rejectedMilestone.reason() + "/" + found.isPresent() + "/" + operator.value());
            });
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ExternalMilestoneEventHandler.class);

    @BeforeEach
    void attachLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
    }

    /** 読みの呼び出しと更新を記録する。 */
    private static class RecordingRepository extends InMemoryTrackingRecordRepository {
        private final List<String> reads = new ArrayList<>();
        private final List<TrackingRecord> updated = new ArrayList<>();
        private RuntimeException readFailure;

        @Override
        public synchronized Optional<TrackingRecord> findByTrackingNumberForUpdate(TrackingNumber trackingNumber) {
            reads.add("forExternalMilestone");
            if (readFailure != null) {
                throw readFailure;
            }
            return super.findByTrackingNumber(trackingNumber);
        }

        @Override
        public synchronized Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber) {
            reads.add("plain");
            return super.findByTrackingNumber(trackingNumber);
        }

        @Override
        public synchronized void update(TrackingRecord trackingRecord) {
            updated.add(trackingRecord);
            super.update(trackingRecord);
        }
    }

    private static MilestoneAdoptedValueAccepted event(
            UUID dataSourceId,
            Source source,
            String trackingNumber,
            String kind,
            Location location,
            UtcInstant occurredAt,
            String externalId,
            UserId operator,
            UtcInstant publishedAt) {
        return new MilestoneAdoptedValueAccepted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                dataSourceId,
                source,
                trackingNumber,
                kind,
                location,
                occurredAt,
                externalId,
                "1",
                operator,
                OPERATOR_COMPANY,
                publishedAt);
    }

    private static MilestoneAdoptedValueAccepted event(String trackingNumber) {
        return event(
                DATA_SOURCE, SOURCE, trackingNumber, "DEPARTURE", TOKYO, OCCURRED_AT, "MS-0001", STEWARD, PUBLISHED_AT);
    }

    private TrackingRecord save(TrackedBookingStatus bookingStatus, TrackingStatus status) {
        TrackingRecord trackingRecord = TrackingRecord.reconstitute(
                TRACKING_NUMBER,
                BOOKING_ID,
                SHIPPER,
                CONSIGNEE,
                bookingStatus,
                schedule(),
                status,
                OptionalInt.empty(),
                List.of(),
                at("2026-11-15T00:00:00Z"),
                at("2026-11-16T03:00:00Z"),
                STARTED_AT,
                0);
        repository.save(trackingRecord);
        return trackingRecord;
    }

    private TrackingRecord stored() {
        return repository.all().getFirst();
    }

    private List<ILoggingEvent> logsAt(Level level) {
        return logs.list.stream().filter(log -> log.getLevel() == level).toList();
    }

    @Nested
    class Bolt39g_受け取り {

        @Test
        void 確定の追跡に受けると下書きを書き出典と外部の識別と操作者とClockの時刻を写す() {
            save(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED);

            handler.on(event(TRACKING_NUMBER.value()));

            assertThat(repository.updated).hasSize(1);
            Milestone milestone = stored().milestones().getFirst();
            assertThat(milestone.state()).isEqualTo(MilestoneState.DRAFT);
            assertThat(milestone.kind()).isEqualTo(MilestoneKind.DEPARTURE);
            assertThat(milestone.source()).isEqualTo(SOURCE);
            assertThat(milestone.externalOrigin()).isEqualTo(new ExternalOrigin(DATA_SOURCE, "MS-0001", PUBLISHED_AT));
            assertThat(milestone.registeredBy()).isEqualTo(STEWARD);
            assertThat(milestone.registeredAt()).isEqualTo(new UtcInstant(NOW));
            assertThat(stored().aggregateVersion()).isEqualTo(1);
            assertThat(rejected.all()).isEmpty();
        }

        @Test
        void 行3_同じDE13の2度目はupdateを呼ばない() {
            save(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED);
            handler.on(event(TRACKING_NUMBER.value()));

            handler.on(event(TRACKING_NUMBER.value()));

            assertThat(repository.updated).hasSize(1);
            assertThat(stored().milestones()).hasSize(1);
        }

        @Test
        void 行23_listenerはロックして読み普通の読みを呼ばない() {
            save(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED);

            handler.on(event(TRACKING_NUMBER.value()));

            assertThat(repository.reads).containsExactly("forExternalMilestone");
        }

        @Test
        void 完了後の追跡には保持のみの実績を書き拒否の記録にしない() {
            save(TrackedBookingStatus.COMPLETED, TrackingStatus.DELIVERED);

            handler.on(event(TRACKING_NUMBER.value()));

            assertThat(stored().milestones().getFirst().state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(rejected.all()).isEmpty();
        }
    }

    @Nested
    class Bolt39g_拒否の記録 {

        private void assertRejected(MilestoneRejectionReason reason, String trackingNumber) {
            assertThat(rejected.all()).singleElement().satisfies(rejectedRecord -> {
                assertThat(rejectedRecord.reason()).isEqualTo(reason);
                assertThat(rejectedRecord.trackingNumber().value()).isEqualTo(trackingNumber);
                assertThat(rejectedRecord.kind()).isEqualTo(MilestoneKind.DEPARTURE);
                assertThat(rejectedRecord.location()).isEqualTo(TOKYO);
                assertThat(rejectedRecord.occurredAt()).isEqualTo(OCCURRED_AT);
                assertThat(rejectedRecord.source()).isEqualTo(SOURCE);
                assertThat(rejectedRecord.dataSourceId()).isEqualTo(DATA_SOURCE);
                assertThat(rejectedRecord.externalId()).isEqualTo("MS-0001");
                assertThat(rejectedRecord.recordedAt()).isEqualTo(new UtcInstant(NOW));
            });
            assertThat(logsAt(Level.WARN)).hasSize(1);
        }

        @Test
        void 行17_取消済みの予約は実績を作らず拒否の記録とWARNに残し版を上げず例外にしない() {
            save(TrackedBookingStatus.CANCELLED, TrackingStatus.PICKUP_SCHEDULED);

            assertThatCode(() -> handler.on(event(TRACKING_NUMBER.value()))).doesNotThrowAnyException();

            assertThat(repository.updated).isEmpty();
            assertThat(stored().milestones()).isEmpty();
            assertThat(stored().aggregateVersion()).isZero();
            assertRejected(MilestoneRejectionReason.BOOKING_CANCELLED, TRACKING_NUMBER.value());
        }

        @Test
        void 行19_取消済みかつ引渡し済みは拒否の記録に残す() {
            save(TrackedBookingStatus.CANCELLED, TrackingStatus.DELIVERED);

            handler.on(event(TRACKING_NUMBER.value()));

            assertThat(repository.updated).isEmpty();
            assertRejected(MilestoneRejectionReason.BOOKING_CANCELLED, TRACKING_NUMBER.value());
        }

        @Test
        void 行20_知らない追跡番号は実績を作らず拒否の記録とWARNに残し例外にしない() {
            assertThatCode(() -> handler.on(event(UNKNOWN_NUMBER))).doesNotThrowAnyException();

            assertThat(repository.updated).isEmpty();
            assertRejected(MilestoneRejectionReason.UNKNOWN_TRACKING_NUMBER, UNKNOWN_NUMBER);
        }

        @Test
        void 行32d_拒否の記録がある同じ出典は追跡記録ができていても実績にせず拒否の記録も増やさない() {
            handler.on(event(TRACKING_NUMBER.value()));
            assertThat(rejected.all()).hasSize(1);
            save(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED);

            handler.on(event(TRACKING_NUMBER.value()));

            assertThat(repository.updated).isEmpty();
            assertThat(stored().milestones()).isEmpty();
            assertThat(rejected.all()).hasSize(1);
        }

        @Test
        void 行20_同じ知らない追跡番号のDE13の2度目は拒否の記録を増やさない() {
            handler.on(event(UNKNOWN_NUMBER));

            handler.on(event(UNKNOWN_NUMBER));

            assertThat(rejected.all()).hasSize(1);
        }

        // --- Bolt 40-13: 拒否した主要実績から有人例外案件を起票する（T-INV-04） ---

        @Test
        void 行40_13a_知らない追跡番号は拒否の記録と同時に追跡記録なしで起票を頼み操作者を渡す() {
            handler.on(event(UNKNOWN_NUMBER));

            assertThat(opened)
                    .containsExactly(MilestoneRejectionReason.UNKNOWN_TRACKING_NUMBER + "/false/" + STEWARD.value());
        }

        @Test
        void 行40_13b_取消済みの予約は追跡記録ありで起票を頼む() {
            save(TrackedBookingStatus.CANCELLED, TrackingStatus.PICKUP_SCHEDULED);

            handler.on(event(TRACKING_NUMBER.value()));

            assertThat(opened).containsExactly(MilestoneRejectionReason.BOOKING_CANCELLED + "/true/" + STEWARD.value());
        }

        @Test
        void 行40_13c_再配信と通常の受け取りでは起票を頼まない() {
            handler.on(event(UNKNOWN_NUMBER));
            handler.on(event(UNKNOWN_NUMBER));
            assertThat(opened).hasSize(1);

            opened.clear();
            save(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED);
            handler.on(event(TRACKING_NUMBER.value()));

            assertThat(opened).isEmpty();
        }

        @Test
        void 行40_13d_起票の失敗は例外のまま上げる_トランザクションに任せる() {
            openFailure = new IllegalStateException("営業カレンダーが未登録");

            MilestoneAdoptedValueAccepted unknown = event(UNKNOWN_NUMBER);

            assertThatThrownBy(() -> handler.on(unknown))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("営業カレンダー");
        }
    }

    @Nested
    class Bolt39g_内容の欠けと失敗 {

        static Stream<Arguments> brokenEvents() {
            Source field = new Source(SourceKind.FIELD_RECORD, RECEIVED_RECORD, ACQUIRED_AT);
            String number = TRACKING_NUMBER.value();
            return Stream.of(
                    Arguments.of(
                            "知らない種類",
                            event(
                                    DATA_SOURCE,
                                    SOURCE,
                                    number,
                                    "FLYING",
                                    TOKYO,
                                    OCCURRED_AT,
                                    "MS",
                                    STEWARD,
                                    PUBLISHED_AT)),
                    Arguments.of(
                            "種類が null",
                            event(DATA_SOURCE, SOURCE, number, null, TOKYO, OCCURRED_AT, "MS", STEWARD, PUBLISHED_AT)),
                    Arguments.of(
                            "形の違う追跡番号",
                            event(
                                    DATA_SOURCE,
                                    SOURCE,
                                    "NOT-A-NUMBER",
                                    "DEPARTURE",
                                    TOKYO,
                                    OCCURRED_AT,
                                    "MS",
                                    STEWARD,
                                    PUBLISHED_AT)),
                    Arguments.of(
                            "追跡番号が null",
                            event(
                                    DATA_SOURCE,
                                    SOURCE,
                                    null,
                                    "DEPARTURE",
                                    TOKYO,
                                    OCCURRED_AT,
                                    "MS",
                                    STEWARD,
                                    PUBLISHED_AT)),
                    Arguments.of(
                            "出典が null",
                            event(
                                    DATA_SOURCE,
                                    null,
                                    number,
                                    "DEPARTURE",
                                    TOKYO,
                                    OCCURRED_AT,
                                    "MS",
                                    STEWARD,
                                    PUBLISHED_AT)),
                    Arguments.of(
                            "出典が外部原本でない",
                            event(
                                    DATA_SOURCE,
                                    field,
                                    number,
                                    "DEPARTURE",
                                    TOKYO,
                                    OCCURRED_AT,
                                    "MS",
                                    STEWARD,
                                    PUBLISHED_AT)),
                    Arguments.of(
                            "操作者が null",
                            event(
                                    DATA_SOURCE,
                                    SOURCE,
                                    number,
                                    "DEPARTURE",
                                    TOKYO,
                                    OCCURRED_AT,
                                    "MS",
                                    null,
                                    PUBLISHED_AT)),
                    Arguments.of(
                            "公開した時刻が null",
                            event(DATA_SOURCE, SOURCE, number, "DEPARTURE", TOKYO, OCCURRED_AT, "MS", STEWARD, null)),
                    Arguments.of(
                            "情報源 ID が null",
                            event(null, SOURCE, number, "DEPARTURE", TOKYO, OCCURRED_AT, "MS", STEWARD, PUBLISHED_AT)),
                    Arguments.of(
                            "外部 ID が空白",
                            event(
                                    DATA_SOURCE,
                                    SOURCE,
                                    number,
                                    "DEPARTURE",
                                    TOKYO,
                                    OCCURRED_AT,
                                    " ",
                                    STEWARD,
                                    PUBLISHED_AT)),
                    Arguments.of(
                            "場所が null",
                            event(
                                    DATA_SOURCE,
                                    SOURCE,
                                    number,
                                    "DEPARTURE",
                                    null,
                                    OCCURRED_AT,
                                    "MS",
                                    STEWARD,
                                    PUBLISHED_AT)),
                    Arguments.of(
                            "発生時刻が null",
                            event(DATA_SOURCE, SOURCE, number, "DEPARTURE", TOKYO, null, "MS", STEWARD, PUBLISHED_AT)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("brokenEvents")
        void 行21_ERRORのログを残して捨て何も書かない(String description, MilestoneAdoptedValueAccepted event) {
            save(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED);

            assertThatCode(() -> handler.on(event)).as(description).doesNotThrowAnyException();

            assertThat(logs.list)
                    .as(description)
                    .singleElement()
                    .satisfies(log -> assertThat(log.getLevel()).isEqualTo(Level.ERROR));
            assertThat(repository.updated).as(description).isEmpty();
            assertThat(rejected.all()).as(description).isEmpty();
            assertThat(repository.reads).as(description).isEmpty();
        }

        static Stream<Arguments> databaseFailures() {
            return Stream.of(
                    Arguments.of(new DataAccessResourceFailureException("接続できない")),
                    Arguments.of(new QueryTimeoutException("ロックの待ちの上限 10 秒を超えた")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("databaseFailures")
        void 行22_DBの失敗は例外を上げて発行記録を未完了に残す(RuntimeException failure) {
            repository.readFailure = failure;
            MilestoneAdoptedValueAccepted event = event(TRACKING_NUMBER.value());

            assertThatThrownBy(() -> handler.on(event)).isSameAs(failure);

            assertThat(logs.list).isEmpty();
            assertThat(rejected.all()).isEmpty();
        }
    }
}
