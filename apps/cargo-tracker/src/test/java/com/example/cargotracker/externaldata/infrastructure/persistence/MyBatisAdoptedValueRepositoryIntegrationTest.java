package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepositoryContract;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateAdoptedValueException;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 採用値のリポジトリの MyBatis 実装を PostgreSQL 18 で確かめる（Bolt 39b3 の行 1・15）。契約を継ぐ。採用値の外部キーの先の情報源・取込・
 * 外部原本・受信記録は、それぞれのリポジトリで書く。テストのトランザクションは契約のクラスの {@code @Transactional} で開き、書いた行は
 * ロールバックで消える。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisAdoptedValueRepositoryIntegrationTest extends AdoptedValueRepositoryContract {

    private static final Instant NOW = Instant.parse("2026-11-03T01:02:03Z");

    @Autowired
    AdoptedValueRepository adoptedValues;

    @Autowired
    IngestionRepository ingestions;

    @Autowired
    ExternalRecordRepository externalRecords;

    @Autowired
    ReceivedRecordRepository receivedRecords;

    @Autowired
    JdbcTemplate jdbc;

    /** この外部版の採用値の契約の外部原本の提供元（テストのインスタンスごと。Bolt 39d3）。 */
    private final String provider = "採用値の提供元-" + UUID.randomUUID();

    @Override
    protected AdoptedValueRepository adoptedValues() {
        return adoptedValues;
    }

    @Override
    protected UUID givenReceivedRecord() {
        return insertAdoptedReceivedRecord(jdbc, ingestions, externalRecords, receivedRecords);
    }

    @Override
    protected UUID givenExternalRecord(String externalId, String externalVersion) {
        return insertExternalRecord(externalRecords, provider, externalId, externalVersion);
    }

    @Override
    protected UUID givenReceivedRecordOf(UUID externalRecordId) {
        return insertAdoptedReceivedRecordOf(jdbc, ingestions, receivedRecords, externalRecordId);
    }

    @Override
    protected UUID givenExternalRecordOf(
            String provider, String recordType, String externalId, String externalVersion) {
        return insertExternalRecord(externalRecords, provider, recordType, externalId, externalVersion);
    }

    @Override
    protected UUID givenReceivedRecordOf(UUID externalRecordId, Instant acquiredAt, Classification classification) {
        return insertReceivedRecordOf(jdbc, ingestions, receivedRecords, externalRecordId, acquiredAt, classification);
    }

    /** 情報源・取込・外部原本・採用の受信記録を書き、受信記録 ID を返す（H2 の契約のテストも使う）。 */
    static UUID insertAdoptedReceivedRecord(
            JdbcTemplate jdbc,
            IngestionRepository ingestions,
            ExternalRecordRepository externalRecords,
            ReceivedRecordRepository receivedRecords) {
        UUID externalRecordId = insertExternalRecord(externalRecords, "採用値の提供元-" + UUID.randomUUID(), "V2611E", "1");
        return insertAdoptedReceivedRecordOf(jdbc, ingestions, receivedRecords, externalRecordId);
    }

    /** 航海の外部原本を書き、外部原本 ID を返す（H2 の契約のテストも使う。Bolt 39d3）。 */
    static UUID insertExternalRecord(
            ExternalRecordRepository externalRecords, String provider, String externalId, String externalVersion) {
        return insertExternalRecord(externalRecords, provider, "VOYAGE", externalId, externalVersion);
    }

    /** 原本種別を決めた外部原本を書き、外部原本 ID を返す（H2 の契約のテストも使う。Bolt 39h）。 */
    static UUID insertExternalRecord(
            ExternalRecordRepository externalRecords,
            String provider,
            String recordType,
            String externalId,
            String externalVersion) {
        ExternalRecord externalRecord = new ExternalRecord(
                UUID.randomUUID(),
                new IdempotencyKey(provider, recordType, externalId, externalVersion),
                PayloadHash.of(new byte[] {1}),
                PayloadObjectKey.newRecordPayload(),
                NOW);
        externalRecords.add(externalRecord);
        return externalRecord.id();
    }

    /** 情報源・取込と、その外部原本を指す採用の受信記録を書き、受信記録 ID を返す（H2 の契約のテストも使う。Bolt 39d3）。 */
    static UUID insertAdoptedReceivedRecordOf(
            JdbcTemplate jdbc,
            IngestionRepository ingestions,
            ReceivedRecordRepository receivedRecords,
            UUID externalRecordId) {
        return insertReceivedRecordOf(
                jdbc, ingestions, receivedRecords, externalRecordId, NOW.minusSeconds(60), Classification.ADOPTED);
    }

    /** 情報源・取込と、その外部原本を指す取得時刻と分類の決まった受信記録を書き、受信記録 ID を返す（H2 の契約のテストも使う。Bolt 39h）。 */
    static UUID insertReceivedRecordOf(
            JdbcTemplate jdbc,
            IngestionRepository ingestions,
            ReceivedRecordRepository receivedRecords,
            UUID externalRecordId,
            Instant acquiredAt,
            Classification classification) {
        DataSource source =
                new DataSource(UUID.randomUUID(), "採用値の提供元-" + UUID.randomUUID(), "VOYAGE", ApprovalStatus.APPROVED);
        MyBatisExternalDataRecordRepositoriesIntegrationTest.insertDataSource(jdbc, source);
        PayloadHash hash = PayloadHash.of(new byte[] {1});
        Ingestion ingestion = new Ingestion(
                UUID.randomUUID(),
                source.id(),
                PayloadObjectKey.newIngestionFile(),
                hash,
                new UserId(UUID.randomUUID()),
                NOW,
                1,
                null,
                0);
        ingestions.add(ingestion);
        ReceivedRecord received = new ReceivedRecord(
                UUID.randomUUID(),
                externalRecordId,
                ingestion.id(),
                hash,
                PayloadObjectKey.newRecordPayload(),
                acquiredAt,
                classification,
                classification == Classification.ADOPTED ? null : "注記",
                NOW,
                null,
                null);
        receivedRecords.add(received);
        return received.id();
    }

    @Test
    void 行15_同じ受信記録の2つ目の採用値は一意制約の違反を原因に持つ名前付きの例外() {
        UUID receivedRecordId = givenReceivedRecord();
        adoptedValues.add(adoptedValue(receivedRecordId));
        AdoptedValue again = adoptedValue(receivedRecordId);

        assertThatThrownBy(() -> adoptedValues.add(again))
                .isInstanceOf(DuplicateAdoptedValueException.class)
                .hasCauseInstanceOf(DuplicateKeyException.class);
    }

    private static AdoptedValue adoptedValue(UUID receivedRecordId) {
        return new AdoptedValue(UUID.randomUUID(), receivedRecordId, TargetType.VOYAGE, "V2611E", "要約", "1", NOW);
    }
}
