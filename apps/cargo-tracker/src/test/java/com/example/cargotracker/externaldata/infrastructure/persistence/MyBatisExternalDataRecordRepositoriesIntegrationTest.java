package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.DecisionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateExternalRecordException;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalDataRecordRepositoriesContract;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.DecisionKind;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 取込・外部原本・受信記録のリポジトリの MyBatis 実装を PostgreSQL 18 で確かめる（Bolt 39b の行 7・9b・10・12、Bolt 39b3 の行 12）。契約を継ぐ。
 * テストのトランザクションは契約のクラスの {@code @Transactional} で開き、書いた行はロールバックで消える（追記専用の表の行も、
 * コミットしないので残らない）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisExternalDataRecordRepositoriesIntegrationTest extends ExternalDataRecordRepositoriesContract {

    @Autowired
    IngestionRepository ingestions;

    @Autowired
    ExternalRecordRepository externalRecords;

    @Autowired
    ReceivedRecordRepository receivedRecords;

    @Autowired
    JdbcTemplate jdbc;

    @Override
    protected IngestionRepository ingestions() {
        return ingestions;
    }

    @Override
    protected ExternalRecordRepository externalRecords() {
        return externalRecords;
    }

    @Override
    protected ReceivedRecordRepository receivedRecords() {
        return receivedRecords;
    }

    @Override
    protected void given(DataSource source) {
        insertDataSource(jdbc, source);
    }

    @Override
    protected Optional<Ingestion> storedIngestion(UUID id) {
        return readIngestion(jdbc, id);
    }

    @Override
    protected Optional<ReceivedRecord> storedReceivedRecord(UUID id) {
        return readReceivedRecord(jdbc, id);
    }

    @Autowired
    ReceivedRecordDecisionRepository receivedRecordDecisions;

    @Override
    protected ReceivedRecordDecisionRepository receivedRecordDecisions() {
        return receivedRecordDecisions;
    }

    @Override
    protected Optional<ReceivedRecordDecision> storedDecision(UUID receivedRecordId) {
        return readDecision(jdbc, receivedRecordId);
    }

    @Override
    protected UUID receivedRecordWithoutIngestion() {
        return insertReceivedRecordWithoutIngestion(jdbc);
    }

    /** 受信記録の判断の行を受信記録 ID で読む（H2 の契約のテストと共有する。Bolt 39d2）。 */
    static Optional<ReceivedRecordDecision> readDecision(JdbcTemplate jdbc, UUID receivedRecordId) {
        return jdbc
                .query(
                        "SELECT id, received_record_id, decision, decided_by, decided_at, reason"
                                + " FROM external_data.received_record_decision WHERE received_record_id = ?",
                        (rs, rowNum) -> new ReceivedRecordDecision(
                                rs.getObject("id", UUID.class),
                                rs.getObject("received_record_id", UUID.class),
                                DecisionKind.valueOf(rs.getString("decision")),
                                new UserId(rs.getObject("decided_by", UUID.class)),
                                rs.getObject("decided_at", OffsetDateTime.class).toInstant(),
                                rs.getString("reason")),
                        receivedRecordId)
                .stream()
                .findFirst();
    }

    /** 取込のない（{@code ingestion_id} が NULL の）キーのない隔離の受信記録を書き、ID を返す（H2 の契約のテストと共有する。Bolt 39d2 の行 22）。 */
    static UUID insertReceivedRecordWithoutIngestion(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.received_record (id, external_record_id, ingestion_id, payload_hash,"
                        + " payload_object_key, acquired_at, classification, classification_note, classified_at)"
                        + " VALUES (?, NULL, NULL, ?, ?, NULL, 'QUARANTINED', ?, ?)",
                id,
                "0".repeat(64),
                "externaldata/records/" + id,
                "取込のない隔離",
                OffsetDateTime.parse("2099-01-01T00:00:00Z"));
        return id;
    }

    @Test
    void 行12_一意制約の違反の名前付きの例外は元の例外を原因に持つ() {
        IdempotencyKey key = new IdempotencyKey("PostgreSQL の提供元-" + UUID.randomUUID(), "VOYAGE", "V-012", "1");
        externalRecords.add(externalRecord(key));
        ExternalRecord again = externalRecord(key);

        assertThatThrownBy(() -> externalRecords.add(again))
                .isInstanceOf(DuplicateExternalRecordException.class)
                .hasCauseInstanceOf(DuplicateKeyException.class);
    }

    @Autowired
    DataSourceRepository dataSources;

    @Autowired
    AdoptedValueRepository adoptedValues;

    /** Bolt 39d3b の行 7（同時の判断に負けた採用）。39b の行 7 と名前が衝突しないよう入れ子にする。 */
    @Nested
    class Bolt39d3b_同時の判断 {

        @ParameterizedTest(name = "勝った判断 {0}")
        @EnumSource(DecisionKind.class)
        void 行7_一意制約に負けた採用はセーブポイントに戻って判断済みを返し採用値を書かずトランザクションは続く(DecisionKind winner) {
            assertLosingAdoption(
                    new AdoptionRepositories(
                            jdbc, ingestions, externalRecords, receivedRecords, receivedRecordDecisions, adoptedValues),
                    dataSources,
                    winner);
        }
    }

    /**
     * Bolt 39d3b の行 7 で使う実リポジトリ（H2 の契約のテストと共有する）。
     *
     * @param jdbc 準備と確かめの SQL
     * @param ingestions 取込
     * @param externalRecords 外部原本
     * @param receivedRecords 受信記録
     * @param decisions 判断（セーブポイントの中で追記する実リポジトリ）
     * @param adoptedValues 採用値
     */
    record AdoptionRepositories(
            JdbcTemplate jdbc,
            IngestionRepository ingestions,
            ExternalRecordRepository externalRecords,
            ReceivedRecordRepository receivedRecords,
            ReceivedRecordDecisionRepository decisions,
            AdoptedValueRepository adoptedValues) {}

    /**
     * Bolt 39d3b の行 7 の本体（H2 の契約のテストと共有する）。読んだ後に別の人の判断が先にコミットした競り合いを、判断の有無の照会だけが偽を返す形で実リポジトリに
     * 差し込む（前例 ReceivedRecordDecisionServiceTest の追記の差し替え）。負けた採用はセーブポイントに戻り、PostgreSQL でもトランザクションを中断させない。
     */
    static void assertLosingAdoption(
            AdoptionRepositories repositories, DataSourceRepository dataSources, DecisionKind winner) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("書いた行はロールバックで消す")
                .isTrue();
        DataSource source = new DataSource(
                UUID.randomUUID(), "採用の提供元-" + UUID.randomUUID(), "VOYAGE", DataSource.ApprovalStatus.APPROVED);
        insertDataSource(repositories.jdbc(), source);
        Instant ingestedAt = Instant.parse("2099-01-01T00:00:00Z");
        Ingestion ingestion = new Ingestion(
                UUID.randomUUID(),
                source.id(),
                PayloadObjectKey.newIngestionFile(),
                PayloadHash.of(new byte[] {8}),
                new UserId(UUID.randomUUID()),
                ingestedAt,
                1,
                null,
                0);
        repositories.ingestions().add(ingestion);
        ExternalRecord externalRecord = new ExternalRecord(
                UUID.randomUUID(),
                new IdempotencyKey(source.provider(), "VOYAGE", "DEMO-201", "3"),
                PayloadHash.of(new byte[] {9}),
                PayloadObjectKey.newRecordPayload(),
                ingestedAt);
        repositories.externalRecords().add(externalRecord);
        VoyageValues values = new VoyageValues(
                "DEMO-201",
                List.of(
                        new PortCallValue(
                                new Location("JPTYO"), null, new UtcInstant(Instant.parse("2099-02-01T00:00:00Z"))),
                        new PortCallValue(
                                new Location("USLAX"), new UtcInstant(Instant.parse("2099-02-10T00:00:00Z")), null)));
        ReceivedRecord quarantine = new ReceivedRecord(
                UUID.randomUUID(),
                externalRecord.id(),
                ingestion.id(),
                PayloadHash.of(new byte[] {10}),
                PayloadObjectKey.newRecordPayload(),
                ingestedAt.minusSeconds(60),
                Classification.QUARANTINED,
                "内容の違う原本",
                ingestedAt,
                new PhysicalLineRange(3, 3),
                ReceivedValue.of(values));
        repositories.receivedRecords().add(quarantine);
        Instant decidedAt = Instant.parse("2099-01-01T00:10:00Z");
        UserId winnerUser = new UserId(UUID.randomUUID());
        repositories
                .decisions()
                .add(
                        winner == DecisionKind.REJECTED
                                ? ReceivedRecordDecision.reject(quarantine, "先の却下。", winnerUser, decidedAt)
                                : ReceivedRecordDecision.adopt(quarantine, null, winnerUser, decidedAt));
        ReceivedRecordDecisionRepository racing = new ReceivedRecordDecisionRepository() {
            @Override
            public void add(ReceivedRecordDecision decision) {
                repositories.decisions().add(decision);
            }

            @Override
            public boolean existsFor(UUID receivedRecordId) {
                return false;
            }
        };
        List<Object> published = new ArrayList<>();
        ReceivedRecordDecisionService service = new ReceivedRecordDecisionService(
                repositories.receivedRecords(),
                racing,
                repositories.ingestions(),
                dataSources,
                repositories.externalRecords(),
                repositories.adoptedValues(),
                published::add,
                Clock.fixed(Instant.parse("2099-01-01T00:20:00Z"), ZoneOffset.UTC));
        AuthenticatedActor steward = new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.DATA_STEWARD),
                "データ責任者",
                "運営会社");

        DecisionOutcome outcome = service.adopt(steward, new AdoptReceivedRecordCommand(quarantine.id(), "負ける採用の理由"));

        assertThat(outcome).isEqualTo(new DecisionOutcome.AlreadyDecided());
        assertThat(published).isEmpty();
        // セーブポイントに戻っていなければ、PostgreSQL はこの照会をトランザクションの中断の誤りにする
        assertThat(repositories.adoptedValues().findByReceivedRecordId(quarantine.id()))
                .isEmpty();
        assertThat(readDecision(repositories.jdbc(), quarantine.id())).get().satisfies(decision -> {
            assertThat(decision.decision()).isEqualTo(winner);
            assertThat(decision.decidedBy()).isEqualTo(winnerUser);
        });
    }

    private static ExternalRecord externalRecord(IdempotencyKey key) {
        return new ExternalRecord(
                UUID.randomUUID(),
                key,
                PayloadHash.of(new byte[] {1}),
                PayloadObjectKey.newRecordPayload(),
                Instant.parse("2026-11-03T01:02:03Z"));
    }

    /** 情報源の行を書く（H2 の契約のテストと共有する）。 */
    static void insertDataSource(JdbcTemplate jdbc, DataSource source) {
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, ?, ?, 0)",
                source.id(),
                source.provider(),
                source.recordType(),
                source.approvalStatus().name());
    }

    /** 取込の行を読む（H2 の契約のテストと共有する）。 */
    static Optional<Ingestion> readIngestion(JdbcTemplate jdbc, UUID id) {
        return jdbc
                .query(
                        "SELECT id, data_source_id, file_object_key, file_hash, ingested_by, ingested_at, received_count,"
                                + " unparsed_from_line, unparsed_line_count"
                                + " FROM external_data.ingestion WHERE id = ?",
                        (rs, rowNum) -> new Ingestion(
                                rs.getObject("id", UUID.class),
                                rs.getObject("data_source_id", UUID.class),
                                new PayloadObjectKey(rs.getString("file_object_key")),
                                new PayloadHash(rs.getString("file_hash")),
                                new UserId(rs.getObject("ingested_by", UUID.class)),
                                rs.getObject("ingested_at", OffsetDateTime.class)
                                        .toInstant(),
                                rs.getInt("received_count"),
                                rs.getObject("unparsed_from_line", Integer.class),
                                rs.getInt("unparsed_line_count")),
                        id)
                .stream()
                .findFirst();
    }

    /** 受信記録の行を読む（H2 の契約のテストと共有する）。 */
    static Optional<ReceivedRecord> readReceivedRecord(JdbcTemplate jdbc, UUID id) {
        return jdbc
                .query(
                        "SELECT id, external_record_id, ingestion_id, payload_hash, payload_object_key, acquired_at,"
                                + " classification, classification_note, classified_at, first_line_number, last_line_number,"
                                + " target_key, value_summary"
                                + " FROM external_data.received_record WHERE id = ?",
                        (rs, rowNum) -> new ReceivedRecord(
                                rs.getObject("id", UUID.class),
                                rs.getObject("external_record_id", UUID.class),
                                rs.getObject("ingestion_id", UUID.class),
                                new PayloadHash(rs.getString("payload_hash")),
                                new PayloadObjectKey(rs.getString("payload_object_key")),
                                instantOrNull(rs.getObject("acquired_at", OffsetDateTime.class)),
                                Classification.valueOf(rs.getString("classification")),
                                rs.getString("classification_note"),
                                rs.getObject("classified_at", OffsetDateTime.class)
                                        .toInstant(),
                                lineRangeOrNull(
                                        rs.getObject("first_line_number", Integer.class),
                                        rs.getObject("last_line_number", Integer.class)),
                                valueOrNull(rs.getString("target_key"), rs.getString("value_summary"))),
                        id)
                .stream()
                .findFirst();
    }

    /** ファイルの誤りと Bolt 39c3 より前の受信記録は行の範囲が NULL（Bolt 39c3 の行 4）。 */
    private static PhysicalLineRange lineRangeOrNull(Integer first, Integer last) {
        return first == null ? null : new PhysicalLineRange(first, last);
    }

    /** キーのない隔離と Bolt 39d3 より前の受信記録は値の 2 列が NULL（Bolt 39d3 の行 5）。 */
    private static ReceivedValue valueOrNull(String targetKey, String summary) {
        return targetKey == null ? null : new ReceivedValue(targetKey, summary);
    }

    /** キーのない隔離の取得時刻は NULL（Bolt 39b2 の行 18）。 */
    private static Instant instantOrNull(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
