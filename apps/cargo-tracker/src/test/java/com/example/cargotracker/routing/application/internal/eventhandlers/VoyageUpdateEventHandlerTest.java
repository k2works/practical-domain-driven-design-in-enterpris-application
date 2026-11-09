package com.example.cargotracker.routing.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated.AdoptedPortCall;
import com.example.cargotracker.routing.acceptance.InMemoryVoyageRepository;
import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentVoyageUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.DuplicateVoyageException;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageAdoption;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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

/** DE-12 を受けて経路設計の航海を書く listener（Bolt 39e の行 10〜13。単体。ポートはメモリの実装と呼び出しの記録）。 */
class VoyageUpdateEventHandlerTest {

    private static final Location TOKYO = new Location("JPTYO");
    private static final Location ROTTERDAM = new Location("NLRTM");
    private static final UtcInstant ACQUIRED = at("2026-11-09T00:00:00Z");
    private static final UtcInstant PUBLISHED = at("2026-11-09T01:00:00Z");
    private static final UtcInstant LATER = at("2026-11-09T02:00:00Z");
    private static final UtcInstant DEPARTURE = at("2026-12-01T00:00:00Z");
    private static final UtcInstant ARRIVAL = at("2026-12-20T00:00:00Z");
    private static final UtcInstant LATER_ARRIVAL = at("2026-12-21T00:00:00Z");
    private static final Source SOURCE =
            new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), ACQUIRED);

    private final RecordingRepository repository = new RecordingRepository();
    private final VoyageUpdateEventHandler handler = new VoyageUpdateEventHandler(repository);

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }

    private static List<AdoptedPortCall> portCalls(UtcInstant arrival) {
        return List.of(new AdoptedPortCall(TOKYO, null, DEPARTURE), new AdoptedPortCall(ROTTERDAM, arrival, null));
    }

    private static VoyageAdoptedValueUpdated event(
            String voyageNumber,
            List<AdoptedPortCall> calls,
            String infoVersion,
            Source source,
            UtcInstant publishedAt) {
        return new VoyageAdoptedValueUpdated(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                source,
                voyageNumber,
                calls,
                infoVersion,
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                publishedAt);
    }

    private static VoyageAdoptedValueUpdated event(UtcInstant publishedAt, UtcInstant arrival) {
        return event("V2611E", portCalls(arrival), "1", SOURCE, publishedAt);
    }

    private static VoyageAdoption adoption(UtcInstant publishedAt) {
        return new VoyageAdoption(
                "V2611E",
                List.of(new PortCall(TOKYO, null, DEPARTURE), new PortCall(ROTTERDAM, ARRIVAL, null)),
                "1",
                SOURCE,
                publishedAt);
    }

    /** 保存と更新の呼び出しを記録し、更新の直前に別の更新を割り込ませる（版を進める）。 */
    private static class RecordingRepository extends InMemoryVoyageRepository {
        private final List<Voyage> saved = new ArrayList<>();
        private final List<Voyage> updated = new ArrayList<>();
        private final Deque<UtcInstant> racingPublishedAts = new ArrayDeque<>();
        private RuntimeException readFailure;

        @Override
        public synchronized java.util.Optional<Voyage> findByVoyageNumber(String voyageNumber) {
            if (readFailure != null) {
                throw readFailure;
            }
            return super.findByVoyageNumber(voyageNumber);
        }

        @Override
        public synchronized void save(Voyage voyage) {
            saved.add(voyage);
            super.save(voyage);
        }

        @Override
        public synchronized void update(Voyage voyage) {
            updated.add(voyage);
            UtcInstant racing = racingPublishedAts.poll();
            if (racing != null) {
                Voyage other = super.findByVoyageNumber(voyage.voyageNumber()).orElseThrow();
                other.apply(adoption(racing));
                super.update(other);
            }
            super.update(voyage);
        }
    }

    private Voyage stored() {
        return repository.findByVoyageNumber("V2611E").orElseThrow();
    }

    @Nested
    class Bolt39e_適用の経路 {

        @Test
        void 行10_航海がないときはsaveを1回呼び決定2の対応で値を写す() {
            VoyageAdoptedValueUpdated event = event(PUBLISHED, ARRIVAL);

            handler.on(event);

            assertThat(repository.saved).hasSize(1);
            assertThat(repository.updated).isEmpty();
            Voyage voyage = stored();
            assertThat(voyage.voyageNumber()).isEqualTo("V2611E");
            assertThat(voyage.portCalls())
                    .containsExactly(new PortCall(TOKYO, null, DEPARTURE), new PortCall(ROTTERDAM, ARRIVAL, null));
            assertThat(voyage.adoptedInfoVersion()).isEqualTo("1");
            assertThat(voyage.source()).isEqualTo(SOURCE);
            assertThat(voyage.acquiredAt()).isEqualTo(ACQUIRED);
            assertThat(voyage.publishedAt()).contains(PUBLISHED);
        }

        @Test
        void 行11_競合したら読み直して適用し直し2回目のupdateが成功する() {
            handler.on(event(PUBLISHED, ARRIVAL));
            repository.racingPublishedAts.add(at("2026-11-09T01:30:00Z"));

            handler.on(event(LATER, LATER_ARRIVAL));

            assertThat(repository.updated).hasSize(2);
            assertThat(stored().publishedAt()).contains(LATER);
            assertThat(stored().portCalls().getLast().arrivalAt()).isEqualTo(LATER_ARRIVAL);
            assertThat(stored().aggregateVersion()).isEqualTo(2);
        }

        @Test
        void 行11_読み直した航海の方が新しければ適用しない() {
            handler.on(event(PUBLISHED, ARRIVAL));
            repository.racingPublishedAts.add(at("2026-11-09T03:00:00Z"));

            handler.on(event(LATER, LATER_ARRIVAL));

            assertThat(repository.updated).hasSize(1);
            assertThat(stored().publishedAt()).contains(at("2026-11-09T03:00:00Z"));
            assertThat(stored().aggregateVersion()).isEqualTo(1);
        }

        @Test
        void 行11_3回とも競合したらConcurrentVoyageUpdateExceptionを上げる() {
            handler.on(event(PUBLISHED, ARRIVAL));
            repository.racingPublishedAts.add(at("2026-11-09T01:10:00Z"));
            repository.racingPublishedAts.add(at("2026-11-09T01:20:00Z"));
            repository.racingPublishedAts.add(at("2026-11-09T01:30:00Z"));
            VoyageAdoptedValueUpdated event = event(LATER, LATER_ARRIVAL);

            assertThatThrownBy(() -> handler.on(event)).isInstanceOf(ConcurrentVoyageUpdateException.class);

            assertThat(repository.updated).hasSize(3);
        }

        @Test
        void 行11_初めての航海の同時で負けたら読み直して適用する() {
            RecordingRepository racing = new RecordingRepository() {
                private boolean raced;

                @Override
                public synchronized void save(Voyage voyage) {
                    if (!raced) {
                        raced = true;
                        // 先に別の DE-12 の保存がコミットされた
                        super.save(Voyage.adoptedFrom(adoption(PUBLISHED)));
                    }
                    super.save(voyage);
                }
            };

            new VoyageUpdateEventHandler(racing).on(event(LATER, LATER_ARRIVAL));

            Voyage voyage = racing.findByVoyageNumber("V2611E").orElseThrow();
            assertThat(voyage.publishedAt()).contains(LATER);
            assertThat(voyage.portCalls().getLast().arrivalAt()).isEqualTo(LATER_ARRIVAL);
            assertThat(racing.updated).hasSize(1);
        }

        @Test
        void 行11_初めての航海の同時が3回続けばDuplicateVoyageExceptionを上げる() {
            InMemoryVoyageRepository alwaysDuplicate = new InMemoryVoyageRepository() {
                @Override
                public synchronized void save(Voyage voyage) {
                    throw new DuplicateVoyageException(voyage.voyageNumber());
                }
            };
            VoyageAdoptedValueUpdated event = event(LATER, LATER_ARRIVAL);
            VoyageUpdateEventHandler duplicateHandler = new VoyageUpdateEventHandler(alwaysDuplicate);

            assertThatThrownBy(() -> duplicateHandler.on(event)).isInstanceOf(DuplicateVoyageException.class);
        }

        @Test
        void 行12_古いDE12ではupdateを呼ばない() {
            handler.on(event(LATER, LATER_ARRIVAL));

            handler.on(event(PUBLISHED, ARRIVAL));

            assertThat(repository.updated).isEmpty();
            assertThat(stored().publishedAt()).contains(LATER);
        }
    }

    @Nested
    class Bolt39e_内容の欠けたDE12 {

        private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
        private final Logger logger = (Logger) LoggerFactory.getLogger(VoyageUpdateEventHandler.class);

        @BeforeEach
        void attachLogs() {
            logs.start();
            logger.addAppender(logs);
        }

        @AfterEach
        void detachLogs() {
            logger.detachAppender(logs);
        }

        static Stream<Arguments> brokenEvents() {
            return Stream.of(
                    Arguments.of("航海番号が null", event(null, portCalls(ARRIVAL), "1", SOURCE, PUBLISHED)),
                    Arguments.of("航海番号が空白", event(" ", portCalls(ARRIVAL), "1", SOURCE, PUBLISHED)),
                    Arguments.of(
                            "寄港 1 つ",
                            event(
                                    "V2611E",
                                    List.of(new AdoptedPortCall(TOKYO, null, DEPARTURE)),
                                    "1",
                                    SOURCE,
                                    PUBLISHED)),
                    Arguments.of(
                            "寄港の港が null",
                            event(
                                    "V2611E",
                                    List.of(
                                            new AdoptedPortCall(null, null, DEPARTURE),
                                            new AdoptedPortCall(ROTTERDAM, ARRIVAL, null)),
                                    "1",
                                    SOURCE,
                                    PUBLISHED)),
                    Arguments.of(
                            "寄港の到着も出発もない",
                            event(
                                    "V2611E",
                                    List.of(
                                            new AdoptedPortCall(TOKYO, null, null),
                                            new AdoptedPortCall(ROTTERDAM, ARRIVAL, null)),
                                    "1",
                                    SOURCE,
                                    PUBLISHED)),
                    Arguments.of(
                            "寄港の出発が到着より前",
                            event(
                                    "V2611E",
                                    List.of(
                                            new AdoptedPortCall(TOKYO, ARRIVAL, DEPARTURE),
                                            new AdoptedPortCall(ROTTERDAM, ARRIVAL, null)),
                                    "1",
                                    SOURCE,
                                    PUBLISHED)),
                    Arguments.of("情報版が null", event("V2611E", portCalls(ARRIVAL), null, SOURCE, PUBLISHED)),
                    Arguments.of("情報版が空白", event("V2611E", portCalls(ARRIVAL), " ", SOURCE, PUBLISHED)),
                    Arguments.of("出典が null", event("V2611E", portCalls(ARRIVAL), "1", null, PUBLISHED)),
                    Arguments.of("公開した時刻が null", event("V2611E", portCalls(ARRIVAL), "1", SOURCE, null)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("brokenEvents")
        void 行13_ERRORのログを残して捨て例外を上げず書かない(String description, VoyageAdoptedValueUpdated event) {
            assertThatCode(() -> handler.on(event)).as(description).doesNotThrowAnyException();

            assertThat(logs.list)
                    .as(description)
                    .singleElement()
                    .satisfies(log -> assertThat(log.getLevel()).isEqualTo(Level.ERROR));
            assertThat(repository.saved).as(description).isEmpty();
            assertThat(repository.updated).as(description).isEmpty();
        }

        @Test
        void 行13_DBの失敗は上げて配り直させる() {
            repository.readFailure = new DataAccessResourceFailureException("接続できない");
            VoyageAdoptedValueUpdated event = event(PUBLISHED, ARRIVAL);

            assertThatThrownBy(() -> handler.on(event)).isInstanceOf(DataAccessResourceFailureException.class);

            assertThat(logs.list).isEmpty();
        }
    }
}
