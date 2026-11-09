package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.DecisionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ConcurrentExternalRecordUpdateException;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryDataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ReceivedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 採用と採用の同時（Bolt 39d4 の行 10）と取込の途中の採用（行 12）を PostgreSQL 18 で確かめる（開始準備の決定 (4)）。本体は H2 のテストと共有する。
 *
 * <p>クラスに {@code @Transactional} を付けない。付けると {@link TransactionTemplate} がテストのトランザクションに合流し、巻き戻りを確かめられない
 * （前例 {@code MyBatisExternalPayloadStorageIntegrationTest}）。コミットした行は残るので、キーは乱数の UUID、取込の時刻は過去にする（絞らない一覧の先頭を確かめる
 * 契約のテストを邪魔しない）。情報源の行は候補で書き（承認済みの行が残ると開始判定の件数を崩す。Bolt 39b のステップ 4）、判断のサービスにはメモリの実装で承認済みの同じ情報源を渡す。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisConcurrentAdoptionIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    IngestionRepository ingestions;

    @Autowired
    ExternalRecordRepository externalRecords;

    @Autowired
    ReceivedRecordRepository receivedRecords;

    @Autowired
    ReceivedRecordDecisionRepository decisions;

    @Autowired
    AdoptedValueRepository adoptedValues;

    private Repositories repositories() {
        return new Repositories(
                jdbc, transactionManager, ingestions, externalRecords, receivedRecords, decisions, adoptedValues);
    }

    @Test
    void 行10_読んだ後に別の採用が先にコミットすると後の採用はトランザクションごと巻き戻り判断も採用値もイベントも残らず基準は先の側() {
        assertLosingAdoptionRollsBack(repositories());
    }

    @Test
    void 行12_外部原本を指す受信記録をコミットしていない間も別の接続の付け替えは待たずに終わる() {
        Scenario scenario = Scenario.committed(repositories());
        TransactionTemplate ingestion = new TransactionTemplate(transactionManager);
        TransactionTemplate adoption = requiresNew(transactionManager);
        PayloadHash adoptedHash = PayloadHash.of(new byte[] {21});
        PayloadObjectKey adoptedObjectKey = PayloadObjectKey.newRecordPayload();

        ingestion.executeWithoutResult(uncommittedIngestion -> {
            receivedRecords.add(scenario.quarantine(PayloadHash.of(new byte[] {20}), 4));
            assertThatCode(() -> adoption.executeWithoutResult(status -> {
                        // 待てば statement_timeout で落ちる（外部キーのロックと付け替えが競合したとき）
                        jdbc.execute("SET LOCAL statement_timeout = '3s'");
                        externalRecords.changeAdoptedPayload(
                                scenario.externalRecord().id(),
                                scenario.externalRecord().payloadHash(),
                                adoptedHash,
                                adoptedObjectKey);
                    }))
                    .doesNotThrowAnyException();
            uncommittedIngestion.setRollbackOnly();
        });

        assertThat(externalRecords.findById(scenario.externalRecord().id()))
                .get()
                .extracting(ExternalRecord::payloadHash)
                .isEqualTo(adoptedHash);
    }

    private static TransactionTemplate requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * 行 10 の本体（H2 のテストと共有する）。負ける側が外部原本を読んだ直後に、別のトランザクションで勝つ側の採用をコミットする形を差し込む（前例 39d3b の行 7。ただし判断のサービスは
     * {@code new} で作るので {@code @Transactional} が効かず、{@link TransactionTemplate} で包む）。
     */
    static void assertLosingAdoptionRollsBack(Repositories repositories) {
        Scenario scenario = Scenario.committed(repositories);
        ReceivedRecord winnerRecord = scenario.storedQuarantine(PayloadHash.of(new byte[] {2}), 2);
        ReceivedRecord loserRecord = scenario.storedQuarantine(PayloadHash.of(new byte[] {3}), 3);
        TransactionTemplate outer = new TransactionTemplate(repositories.transactionManager());
        TransactionTemplate other = requiresNew(repositories.transactionManager());
        ReceivedRecordDecisionService winner = scenario.service(repositories.externalRecords(), new ArrayList<>());
        boolean[] raced = {false};
        ExternalRecordRepository racing = new ExternalRecordRepository() {
            @Override
            public Optional<ExternalRecord> findByKey(IdempotencyKey key) {
                return repositories.externalRecords().findByKey(key);
            }

            @Override
            public Optional<ExternalRecord> findById(UUID externalRecordId) {
                Optional<ExternalRecord> read = repositories.externalRecords().findById(externalRecordId);
                if (!raced[0]) {
                    raced[0] = true;
                    other.executeWithoutResult(status -> assertThat(winner.adopt(
                                    scenario.steward(), new AdoptReceivedRecordCommand(winnerRecord.id(), "先の採用")))
                            .isEqualTo(new DecisionOutcome.AdoptedQuarantine(winnerRecord.id())));
                }
                return read;
            }

            @Override
            public void add(ExternalRecord externalRecord) {
                repositories.externalRecords().add(externalRecord);
            }

            @Override
            public void changeAdoptedPayload(
                    UUID externalRecordId,
                    PayloadHash expectedHash,
                    PayloadHash adoptedHash,
                    PayloadObjectKey adoptedObjectKey) {
                repositories
                        .externalRecords()
                        .changeAdoptedPayload(externalRecordId, expectedHash, adoptedHash, adoptedObjectKey);
            }
        };
        List<Object> loserEvents = new ArrayList<>();
        ReceivedRecordDecisionService loser = scenario.service(racing, loserEvents);

        assertThatThrownBy(() -> outer.executeWithoutResult(status ->
                        loser.adopt(scenario.steward(), new AdoptReceivedRecordCommand(loserRecord.id(), "後の採用"))))
                .isInstanceOf(ConcurrentExternalRecordUpdateException.class);

        assertThat(loserEvents).isEmpty();
        assertThat(repositories.decisions().existsFor(loserRecord.id())).isFalse();
        assertThat(repositories.adoptedValues().findByReceivedRecordId(loserRecord.id()))
                .isEmpty();
        assertThat(repositories.decisions().existsFor(winnerRecord.id())).isTrue();
        assertThat(repositories.adoptedValues().findByReceivedRecordId(winnerRecord.id()))
                .isPresent();
        assertThat(repositories
                        .externalRecords()
                        .findById(scenario.externalRecord().id()))
                .get()
                .satisfies(found -> {
                    assertThat(found.payloadHash()).isEqualTo(winnerRecord.payloadHash());
                    assertThat(found.payloadObjectKey()).isEqualTo(winnerRecord.payloadObjectKey());
                });
        // コミットした隔離を判断を待つ一覧に残さない（ほかのテストの一覧と件数を邪魔しない）
        outer.executeWithoutResult(status -> assertThat(
                        winner.reject(scenario.steward(), new RejectReceivedRecordCommand(loserRecord.id(), "片付ける。")))
                .isEqualTo(new DecisionOutcome.Rejected(loserRecord.id())));
    }

    /**
     * 同時の確かめに使う実リポジトリ（H2 のテストと共有する）。
     *
     * @param jdbc 情報源の行を書く
     * @param transactionManager トランザクションを分ける
     * @param ingestions 取込
     * @param externalRecords 外部原本
     * @param receivedRecords 受信記録
     * @param decisions 判断
     * @param adoptedValues 採用値
     */
    record Repositories(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            IngestionRepository ingestions,
            ExternalRecordRepository externalRecords,
            ReceivedRecordRepository receivedRecords,
            ReceivedRecordDecisionRepository decisions,
            AdoptedValueRepository adoptedValues) {}

    /**
     * コミットした情報源・取込・外部原本（キーは乱数の UUID、時刻は過去）。
     *
     * @param repositories 実リポジトリ
     * @param approvedSource 判断のサービスに渡す承認済みの情報源（表の行は候補）
     * @param ingestion 取込
     * @param externalRecord 外部原本
     */
    record Scenario(
            Repositories repositories, DataSource approvedSource, Ingestion ingestion, ExternalRecord externalRecord) {

        private static final Instant INGESTED_AT = Instant.parse("2026-01-01T00:00:00Z");

        private static final AuthenticatedActor STEWARD = new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.DATA_STEWARD),
                "データ責任者",
                "運営会社");

        static Scenario committed(Repositories repositories) {
            UUID sourceId = UUID.randomUUID();
            String provider = "同時の採用の提供元-" + UUID.randomUUID();
            MyBatisExternalDataRecordRepositoriesIntegrationTest.insertDataSource(
                    repositories.jdbc(),
                    new DataSource(sourceId, provider, "VOYAGE", DataSource.ApprovalStatus.CANDIDATE));
            Ingestion ingestion = new Ingestion(
                    UUID.randomUUID(),
                    sourceId,
                    PayloadObjectKey.newIngestionFile(),
                    PayloadHash.of(new byte[] {8}),
                    new UserId(UUID.randomUUID()),
                    INGESTED_AT,
                    2,
                    null,
                    0);
            ExternalRecord externalRecord = new ExternalRecord(
                    UUID.randomUUID(),
                    new IdempotencyKey(provider, "VOYAGE", "DEMO-" + UUID.randomUUID(), "3"),
                    PayloadHash.of(new byte[] {1}),
                    PayloadObjectKey.newRecordPayload(),
                    INGESTED_AT);
            new TransactionTemplate(repositories.transactionManager()).executeWithoutResult(status -> {
                repositories.ingestions().add(ingestion);
                repositories.externalRecords().add(externalRecord);
            });
            return new Scenario(
                    repositories,
                    new DataSource(sourceId, provider, "VOYAGE", DataSource.ApprovalStatus.APPROVED),
                    ingestion,
                    externalRecord);
        }

        ReceivedRecord quarantine(PayloadHash hash, int line) {
            VoyageValues values = new VoyageValues(
                    "DEMO-201",
                    List.of(
                            new PortCallValue(
                                    new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-02-01T00:00:00Z"))),
                            new PortCallValue(
                                    new Location("USLAX"),
                                    new UtcInstant(Instant.parse("2026-02-10T00:00:00Z")
                                            .plusSeconds(line)),
                                    null)));
            return new ReceivedRecord(
                    UUID.randomUUID(),
                    externalRecord.id(),
                    ingestion.id(),
                    hash,
                    PayloadObjectKey.newRecordPayload(),
                    INGESTED_AT.minusSeconds(60),
                    Classification.QUARANTINED,
                    "内容の違う原本",
                    INGESTED_AT,
                    new PhysicalLineRange(line, line),
                    ReceivedValue.of(values));
        }

        ReceivedRecord storedQuarantine(PayloadHash hash, int line) {
            ReceivedRecord quarantine = quarantine(hash, line);
            new TransactionTemplate(repositories.transactionManager())
                    .executeWithoutResult(
                            status -> repositories.receivedRecords().add(quarantine));
            return quarantine;
        }

        ReceivedRecordDecisionService service(ExternalRecordRepository externalRecords, List<Object> published) {
            InMemoryDataSourceRepository approved = new InMemoryDataSourceRepository();
            approved.add(approvedSource);
            return new ReceivedRecordDecisionService(
                    repositories.receivedRecords(),
                    repositories.decisions(),
                    repositories.ingestions(),
                    approved,
                    externalRecords,
                    repositories.adoptedValues(),
                    published::add,
                    Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));
        }

        AuthenticatedActor steward() {
            return STEWARD;
        }
    }
}
