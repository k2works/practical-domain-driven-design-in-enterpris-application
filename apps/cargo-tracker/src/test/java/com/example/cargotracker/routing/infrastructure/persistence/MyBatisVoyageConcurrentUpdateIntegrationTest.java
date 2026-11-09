package com.example.cargotracker.routing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated.AdoptedPortCall;
import com.example.cargotracker.routing.application.internal.eventhandlers.VoyageUpdateEventHandler;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageAdoption;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 同じ航海の DE-12 の同時（Bolt 39e の行 19・20）と、読みと書きの同時（行 45）を PostgreSQL 18 で確かめる。本体は H2 のテストと共有する。
 *
 * <p>クラスに {@code @Transactional} を付けない（別のトランザクションで確かめる。前例 {@code MyBatisConcurrentAdoptionIntegrationTest}）。コミットした
 * 航海は finally で消し、港は JPTYO → USLAX にする（共有の DB のほかのテストの候補につながらない）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisVoyageConcurrentUpdateIntegrationTest {

    private static final Instant ACQUIRED = Instant.parse("2026-11-09T00:00:00Z");
    private static final UtcInstant DEPARTURE = new UtcInstant(Instant.parse("2097-12-01T00:00:00Z"));
    private static final UtcInstant ARRIVAL = new UtcInstant(Instant.parse("2097-12-20T00:00:00Z"));
    private static final UtcInstant OLDER_ARRIVAL = new UtcInstant(Instant.parse("2097-12-18T00:00:00Z"));
    private static final UtcInstant NEWER_ARRIVAL = new UtcInstant(Instant.parse("2097-12-22T00:00:00Z"));
    private static final UtcInstant OLDER_PUBLISHED = new UtcInstant(ACQUIRED.plusSeconds(3600));
    private static final UtcInstant NEWER_PUBLISHED = new UtcInstant(ACQUIRED.plusSeconds(7200));
    private static final Location TOKYO = new Location("JPTYO");
    private static final Location LOS_ANGELES = new Location("USLAX");

    @Autowired
    VoyageRepository voyages;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void Bolt39e_行19_既存の行で読んだ後に新しいDE12が先にコミットすると古いDE12は適用されず版は1() throws Exception {
        assertConcurrentAdoptions(new Repositories(voyages, jdbc, transactionManager), true, true);
    }

    @Test
    void Bolt39e_行19_既存の行で読んだ後に古いDE12が先にコミットすると読み直して新しいDE12が適用され版は2() throws Exception {
        assertConcurrentAdoptions(new Repositories(voyages, jdbc, transactionManager), true, false);
    }

    @Test
    void Bolt39e_行20_初めての航海で新しいDE12が先にコミットすると負けた古いDE12は重複から読み直して適用されない() throws Exception {
        assertConcurrentAdoptions(new Repositories(voyages, jdbc, transactionManager), false, true);
    }

    @Test
    void Bolt39e_行20_初めての航海で古いDE12が先にコミットすると負けた新しいDE12は重複から読み直して適用される() throws Exception {
        assertConcurrentAdoptions(new Repositories(voyages, jdbc, transactionManager), false, false);
    }

    @Test
    void Bolt39e_行45_読みの途中で更新がコミットされても寄港が空の航海を読まず例外にならない() throws Exception {
        assertReadsDuringWrites(new Repositories(voyages, jdbc, transactionManager));
    }

    /** H2 と PostgreSQL のテストが共有する入力。 */
    record Repositories(VoyageRepository voyages, JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {

        TransactionTemplate transaction() {
            return new TransactionTemplate(transactionManager);
        }

        void delete(String voyageNumber) {
            jdbc.update("DELETE FROM routing.port_call WHERE voyage_number = ?", voyageNumber);
            jdbc.update("DELETE FROM routing.voyage WHERE voyage_number = ?", voyageNumber);
        }

        Voyage read(String voyageNumber) {
            return transaction()
                    .execute(status -> voyages.findByVoyageNumber(voyageNumber).orElseThrow());
        }
    }

    /** 最初の読みの直後で、先の DE-12 のコミットを待つリポジトリ（読んだ後に先を越される順を作る）。 */
    private static final class PausingAfterFirstRead implements VoyageRepository {
        private final VoyageRepository delegate;
        private final CountDownLatch read;
        private final CountDownLatch resume;
        private final AtomicBoolean paused = new AtomicBoolean();

        PausingAfterFirstRead(VoyageRepository delegate, CountDownLatch read, CountDownLatch resume) {
            this.delegate = delegate;
            this.read = read;
            this.resume = resume;
        }

        @Override
        public Optional<Voyage> findByVoyageNumber(String voyageNumber) {
            Optional<Voyage> found = delegate.findByVoyageNumber(voyageNumber);
            if (paused.compareAndSet(false, true)) {
                read.countDown();
                await(resume);
            }
            return found;
        }

        @Override
        public List<Voyage> findByVoyageNumbers(Collection<String> voyageNumbers) {
            return delegate.findByVoyageNumbers(voyageNumbers);
        }

        @Override
        public List<Voyage> findCandidateVoyages(UtcInstant departingAfter) {
            return delegate.findCandidateVoyages(departingAfter);
        }

        @Override
        public void save(Voyage voyage) {
            delegate.save(voyage);
        }

        @Override
        public void update(Voyage voyage) {
            delegate.update(voyage);
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(20, TimeUnit.SECONDS)) {
                    throw new AssertionError("先の DE-12 のコミットを待ちきれない");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private static VoyageAdoptedValueUpdated event(String voyageNumber, UtcInstant arrival, UtcInstant publishedAt) {
        return new VoyageAdoptedValueUpdated(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), new UtcInstant(ACQUIRED)),
                voyageNumber,
                List.of(new AdoptedPortCall(TOKYO, null, DEPARTURE), new AdoptedPortCall(LOS_ANGELES, arrival, null)),
                "1",
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                publishedAt);
    }

    /**
     * 負ける側のトランザクションが航海を読んだ後に、先の DE-12 をコミットさせ、それから負ける側を続ける。どちらの順でも公開した時刻の新しい方が残る。
     *
     * @param existing 既存の行があるとき真（行 19）、初めての航海のとき偽（行 20）
     * @param winnerIsNewer 先にコミットするのが新しい DE-12 のとき真
     */
    static void assertConcurrentAdoptions(Repositories repositories, boolean existing, boolean winnerIsNewer)
            throws Exception {
        String voyageNumber = "C-" + UUID.randomUUID().toString().substring(0, 12);
        try {
            if (existing) {
                repositories
                        .transaction()
                        .executeWithoutResult(status -> repositories
                                .voyages()
                                .save(Voyage.adoptedFrom(new VoyageAdoption(
                                        voyageNumber,
                                        List.of(
                                                new PortCall(TOKYO, null, DEPARTURE),
                                                new PortCall(LOS_ANGELES, ARRIVAL, null)),
                                        "1",
                                        new Source(
                                                SourceKind.EXTERNAL_RECORD,
                                                UUID.randomUUID().toString(),
                                                new UtcInstant(ACQUIRED)),
                                        new UtcInstant(ACQUIRED)))));
            }
            VoyageAdoptedValueUpdated older = event(voyageNumber, OLDER_ARRIVAL, OLDER_PUBLISHED);
            VoyageAdoptedValueUpdated newer = event(voyageNumber, NEWER_ARRIVAL, NEWER_PUBLISHED);
            CountDownLatch loserRead = new CountDownLatch(1);
            CountDownLatch winnerCommitted = new CountDownLatch(1);
            VoyageUpdateEventHandler loser = new VoyageUpdateEventHandler(
                    new PausingAfterFirstRead(repositories.voyages(), loserRead, winnerCommitted));
            VoyageUpdateEventHandler winner = new VoyageUpdateEventHandler(repositories.voyages());
            VoyageAdoptedValueUpdated winning = winnerIsNewer ? newer : older;
            VoyageAdoptedValueUpdated losing = winnerIsNewer ? older : newer;

            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                Future<?> losingDelivery = executor.submit(
                        () -> repositories.transaction().executeWithoutResult(status -> loser.on(losing)));
                assertThat(loserRead.await(20, TimeUnit.SECONDS)).isTrue();
                repositories.transaction().executeWithoutResult(status -> winner.on(winning));
                winnerCommitted.countDown();
                losingDelivery.get(30, TimeUnit.SECONDS);
            }

            Voyage result = repositories.read(voyageNumber);
            assertThat(result.publishedAt()).contains(NEWER_PUBLISHED);
            assertThat(result.portCalls().getLast().arrivalAt()).isEqualTo(NEWER_ARRIVAL);
            long expectedVersion = (existing ? 1 : 0) + (winnerIsNewer ? 0 : 1);
            assertThat(result.aggregateVersion()).isEqualTo(expectedVersion);
        } finally {
            repositories.delete(voyageNumber);
        }
    }

    /** 書き手が航海を保存・更新している間に読み手が読み続け、例外が 1 つも出ない（読みは航海と寄港の 1 文）。 */
    static void assertReadsDuringWrites(Repositories repositories) throws Exception {
        String prefix = "C-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        List<String> written = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicBoolean writing = new AtomicBoolean(true);
        UtcInstant pastJudged = new UtcInstant(Instant.parse("2097-01-01T00:00:00Z"));
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<?> candidateReader = executor.submit(
                    () -> read(writing, failures, () -> repositories.voyages().findCandidateVoyages(pastJudged)));
            Future<?> numberReader = executor.submit(() ->
                    read(writing, failures, () -> repositories.voyages().findByVoyageNumbers(List.copyOf(written))));
            Future<?> writer = executor.submit(() -> {
                try {
                    for (int i = 0; i < 30; i++) {
                        String number = prefix + i;
                        written.add(number);
                        writeAndUpdate(repositories, number);
                    }
                } catch (RuntimeException e) {
                    failures.add(e);
                } finally {
                    writing.set(false);
                }
            });
            writer.get(60, TimeUnit.SECONDS);
            candidateReader.get(60, TimeUnit.SECONDS);
            numberReader.get(60, TimeUnit.SECONDS);
        } finally {
            written.forEach(repositories::delete);
        }
        assertThat(failures).isEmpty();
    }

    private static void read(AtomicBoolean writing, List<Throwable> failures, Runnable reader) {
        while (writing.get()) {
            try {
                reader.run();
            } catch (RuntimeException e) {
                failures.add(e);
                return;
            }
        }
    }

    private static void writeAndUpdate(Repositories repositories, String number) {
        repositories
                .transaction()
                .executeWithoutResult(status -> repositories
                        .voyages()
                        .save(Voyage.adoptedFrom(new VoyageAdoption(
                                number,
                                List.of(new PortCall(TOKYO, null, DEPARTURE), new PortCall(LOS_ANGELES, ARRIVAL, null)),
                                "1",
                                new Source(
                                        SourceKind.EXTERNAL_RECORD,
                                        UUID.randomUUID().toString(),
                                        new UtcInstant(ACQUIRED)),
                                new UtcInstant(ACQUIRED)))));
        repositories.transaction().executeWithoutResult(status -> {
            Voyage voyage = repositories.voyages().findByVoyageNumber(number).orElseThrow();
            voyage.apply(new VoyageAdoption(
                    number,
                    List.of(new PortCall(TOKYO, null, DEPARTURE), new PortCall(LOS_ANGELES, NEWER_ARRIVAL, null)),
                    "1",
                    new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), new UtcInstant(ACQUIRED)),
                    NEWER_PUBLISHED));
            repositories.voyages().update(voyage);
        });
    }
}
