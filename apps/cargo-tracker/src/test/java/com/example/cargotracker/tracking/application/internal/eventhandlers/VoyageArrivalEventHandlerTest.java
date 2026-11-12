package com.example.cargotracker.tracking.application.internal.eventhandlers;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.CONSIGNEE;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.SHIPPER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.STARTED_AT;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated.AdoptedPortCall;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;

/**
 * DE-12 を受けて最後の区間の航海の到着予定を追跡記録に受けさせる listener（Bolt 39g4b の行 13。単体。ポートはメモリの実装と呼び出しの記録）。予定は
 * 2 区間（区間 2 は V200 で KRPUS から USLAX、到着予定 2026-11-15 00:00）。
 */
class VoyageArrivalEventHandlerTest {

    private static final UtcInstant ARRIVAL = at("2026-11-15T10:00:00Z");
    private static final UtcInstant ACQUIRED_AT = at("2026-11-06T01:00:00Z");
    private static final UtcInstant PUBLISHED_AT = at("2026-11-06T02:00:00Z");
    private static final Source SOURCE = new Source(SourceKind.EXTERNAL_RECORD, "rr-1", ACQUIRED_AT);
    private static final TrackingNumber FIRST = new TrackingNumber("CTAAAAAAAAAAAA");
    private static final TrackingNumber SECOND = new TrackingNumber("CTBBBBBBBBBBBB");

    private final RecordingRepository repository = new RecordingRepository();
    private final VoyageArrivalEventHandler handler = new VoyageArrivalEventHandler(repository);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(VoyageArrivalEventHandler.class);

    @BeforeEach
    void attachLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
    }

    private static List<AdoptedPortCall> portCalls() {
        return List.of(
                new AdoptedPortCall(new Location("KRPUS"), null, at("2026-11-04T00:00:00Z")),
                new AdoptedPortCall(new Location("USLAX"), ARRIVAL, null));
    }

    private static VoyageAdoptedValueUpdated event(
            String voyageNumber, List<AdoptedPortCall> portCalls, Source source, UtcInstant publishedAt) {
        return new VoyageAdoptedValueUpdated(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                source,
                voyageNumber,
                portCalls,
                "1",
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                publishedAt);
    }

    private static VoyageAdoptedValueUpdated event() {
        return event("V200", portCalls(), SOURCE, PUBLISHED_AT);
    }

    private void start(TrackingNumber trackingNumber) {
        repository.save(
                TrackingRecord.start(trackingNumber, UUID.randomUUID(), SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                        .trackingRecord());
    }

    @Test
    void 行13a_対象の追跡を追跡番号の順に押さえてから読み直して受け更新する() {
        start(SECOND);
        start(FIRST);

        handler.on(event());

        assertThat(repository.calls)
                .containsExactly(
                        "finalLeg V200",
                        "forUpdate " + FIRST.value(),
                        "update " + FIRST.value(),
                        "forUpdate " + SECOND.value(),
                        "update " + SECOND.value());
        assertThat(repository.findByTrackingNumber(FIRST).orElseThrow().latestEta())
                .isEqualTo(ARRIVAL);
        assertThat(repository.findByTrackingNumber(SECOND).orElseThrow().latestEta())
                .isEqualTo(ARRIVAL);
        assertThat(repository.findByTrackingNumber(FIRST).orElseThrow().aggregateVersion())
                .isEqualTo(1);
    }

    @Test
    void 行13b_対象がなければ何もしない() {
        start(FIRST);

        handler.on(event("V999", portCalls(), SOURCE, PUBLISHED_AT));

        assertThat(repository.calls).containsExactly("finalLeg V999");
    }

    @Test
    void 行13a_受けなかった追跡は更新しない() {
        start(FIRST);
        handler.on(event());
        repository.calls.clear();

        handler.on(event());

        assertThat(repository.calls).containsExactly("finalLeg V200", "forUpdate " + FIRST.value());
    }

    static Stream<Arguments> 行13c() {
        return Stream.of(
                Arguments.of("航海番号が空", event(" ", portCalls(), SOURCE, PUBLISHED_AT)),
                Arguments.of("航海番号が null", event(null, portCalls(), SOURCE, PUBLISHED_AT)),
                Arguments.of("寄港の並びが 2 つ未満", event("V200", List.of(portCalls().getFirst()), SOURCE, PUBLISHED_AT)),
                Arguments.of(
                        "寄港の港が null",
                        event(
                                "V200",
                                List.of(portCalls().getFirst(), new AdoptedPortCall(null, ARRIVAL, null)),
                                SOURCE,
                                PUBLISHED_AT)),
                // 出典の参照が空・取得時刻が null の出典と、寄港の並びが null の DE-12 は作れない（共有カーネルの Source と DE-12 の型が拒む）。寄港の並びの null は
                // 値の側の作り方で確かめ、出典の 2 つは SourceTest が縛る（Bolt 39g4c で値の側の届かない分岐を消した）
                Arguments.of("出典が null", event("V200", portCalls(), null, PUBLISHED_AT)),
                Arguments.of("公開した時刻が null", event("V200", portCalls(), SOURCE, null)));
    }

    @Test
    void 行13c_値の側は寄港の並びのnullを内容の欠けにする() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        com.example.cargotracker.tracking.domain.model.valueobjects.VoyageArrival.portCallsOf(null))
                .isInstanceOf(
                        com.example.cargotracker.tracking.domain.model.valueobjects.VoyageArrival
                                .InvalidEventValueException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void 行13c(String name, VoyageAdoptedValueUpdated event) {
        start(FIRST);

        handler.on(event);

        assertThat(repository.calls).isEmpty();
        assertThat(logs.list)
                .singleElement()
                .satisfies(log -> assertThat(log.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void 行13d_ロックの待ちの上限を超えたら例外を上げる() {
        start(FIRST);
        repository.lockFailure = new QueryTimeoutException("ロックの待ちの上限");

        assertThatThrownBy(() -> handler.on(event())).isSameAs(repository.lockFailure);
        assertThat(repository.findByTrackingNumber(FIRST).orElseThrow().voyageArrival())
                .isEmpty();
    }

    /**
     * Bolt 39g4c の行 1 (d)。メモリの実装の照会を直接呼び、最後の区間の航海で絞ることを戻り値で確かめる（Bolt 39g4b の終了報告の議題 3。listener を
     * 通すと集約が最後の区間でない知らせを受けないので、すべての区間で引く変異が隠れる）。追跡 X は V200 が区間 1、追跡 Y は V200 が最後の区間。
     */
    @org.junit.jupiter.api.Nested
    class Bolt39g4c_行1d_メモリの実装の照会 {

        private static final TrackingNumber X = new TrackingNumber("CTXXXXXXXXXXXX");
        private static final TrackingNumber Y = new TrackingNumber("CTYYYYYYYYYYYY");

        private final InMemoryTrackingRecordRepository memory = new InMemoryTrackingRecordRepository();

        @BeforeEach
        void startTwo() {
            com.example.cargotracker.tracking.domain.model.valueobjects.Schedule v200First =
                    new com.example.cargotracker.tracking.domain.model.valueobjects.Schedule(
                            "RC-2026-0002",
                            1,
                            List.of(
                                    com.example.cargotracker.tracking.domain.model.TrackingFixture.leg(
                                            "V200", "KRPUS", "CNSHA", "2026-11-04T00:00:00Z", "2026-11-06T00:00:00Z"),
                                    com.example.cargotracker.tracking.domain.model.TrackingFixture.leg(
                                            "V300", "CNSHA", "USLAX", "2026-11-07T00:00:00Z", "2026-11-15T00:00:00Z")));
            memory.save(TrackingRecord.start(X, UUID.randomUUID(), SHIPPER, CONSIGNEE, v200First, STARTED_AT)
                    .trackingRecord());
            memory.save(TrackingRecord.start(Y, UUID.randomUUID(), SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                    .trackingRecord());
        }

        @Test
        void 中間の区間の航海では引かない() {
            assertThat(memory.findTrackingNumbersByFinalLegVoyage("V100")).isEmpty();
        }

        @Test
        void 最後の区間の航海の追跡だけを引く() {
            assertThat(memory.findTrackingNumbersByFinalLegVoyage("V200")).containsExactly(Y);
        }
    }

    /** 呼び出しの順を記録するメモリの実装（押さえる前に読んだ値を使わないことを、押さえる操作だけで読むことで確かめる）。 */
    private static final class RecordingRepository extends InMemoryTrackingRecordRepository {
        private final List<String> calls = new ArrayList<>();
        private RuntimeException lockFailure;

        @Override
        public synchronized List<TrackingNumber> findTrackingNumbersByFinalLegVoyage(String voyageNumber) {
            calls.add("finalLeg " + voyageNumber);
            return super.findTrackingNumbersByFinalLegVoyage(voyageNumber);
        }

        @Override
        public synchronized Optional<TrackingRecord> findByTrackingNumberForUpdate(TrackingNumber trackingNumber) {
            calls.add("forUpdate " + trackingNumber.value());
            if (lockFailure != null) {
                throw lockFailure;
            }
            return super.findByTrackingNumber(trackingNumber);
        }

        @Override
        public synchronized Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber) {
            return super.findByTrackingNumber(trackingNumber);
        }

        @Override
        public synchronized void update(TrackingRecord trackingRecord) {
            calls.add("update " + trackingRecord.trackingNumber().value());
            super.update(trackingRecord);
        }
    }
}
