package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepositoryContract;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
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

    @Override
    protected AdoptedValueRepository adoptedValues() {
        return adoptedValues;
    }

    @Override
    protected UUID givenReceivedRecord() {
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
        ExternalRecord externalRecord = new ExternalRecord(
                UUID.randomUUID(),
                new IdempotencyKey(source.provider(), "VOYAGE", "V2611E", "1"),
                hash,
                PayloadObjectKey.newRecordPayload(),
                NOW);
        externalRecords.add(externalRecord);
        ReceivedRecord received = new ReceivedRecord(
                UUID.randomUUID(),
                externalRecord.id(),
                ingestion.id(),
                hash,
                externalRecord.payloadObjectKey(),
                NOW.minusSeconds(60),
                Classification.ADOPTED,
                null,
                NOW);
        receivedRecords.add(received);
        return received.id();
    }

    @Test
    void 行15_同じ受信記録の2つ目の採用値は一意制約で拒む() {
        UUID receivedRecordId = givenReceivedRecord();
        adoptedValues.add(adoptedValue(receivedRecordId));
        AdoptedValue again = adoptedValue(receivedRecordId);

        assertThatThrownBy(() -> adoptedValues.add(again)).isInstanceOf(DuplicateKeyException.class);
    }

    private static AdoptedValue adoptedValue(UUID receivedRecordId) {
        return new AdoptedValue(UUID.randomUUID(), receivedRecordId, TargetType.VOYAGE, "V2611E", "要約", "1", NOW);
    }
}
