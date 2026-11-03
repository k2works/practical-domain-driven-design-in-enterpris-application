package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalDataRecordRepositoriesContract;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.domain.UserId;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 取込・外部原本・受信記録のリポジトリの MyBatis 実装を PostgreSQL 18 で確かめる（Bolt 39b の行 7・9b・10・12）。契約を継ぐ。
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
                        "SELECT id, data_source_id, file_object_key, file_hash, ingested_by, ingested_at, received_count"
                                + " FROM external_data.ingestion WHERE id = ?",
                        (rs, rowNum) -> new Ingestion(
                                rs.getObject("id", UUID.class),
                                rs.getObject("data_source_id", UUID.class),
                                new PayloadObjectKey(rs.getString("file_object_key")),
                                new PayloadHash(rs.getString("file_hash")),
                                new UserId(rs.getObject("ingested_by", UUID.class)),
                                rs.getObject("ingested_at", OffsetDateTime.class)
                                        .toInstant(),
                                rs.getInt("received_count")),
                        id)
                .stream()
                .findFirst();
    }

    /** 受信記録の行を読む（H2 の契約のテストと共有する）。 */
    static Optional<ReceivedRecord> readReceivedRecord(JdbcTemplate jdbc, UUID id) {
        return jdbc
                .query(
                        "SELECT id, external_record_id, ingestion_id, payload_hash, payload_object_key, acquired_at,"
                                + " classification, classification_note, classified_at"
                                + " FROM external_data.received_record WHERE id = ?",
                        (rs, rowNum) -> new ReceivedRecord(
                                rs.getObject("id", UUID.class),
                                rs.getObject("external_record_id", UUID.class),
                                rs.getObject("ingestion_id", UUID.class),
                                new PayloadHash(rs.getString("payload_hash")),
                                new PayloadObjectKey(rs.getString("payload_object_key")),
                                rs.getObject("acquired_at", OffsetDateTime.class)
                                        .toInstant(),
                                Classification.valueOf(rs.getString("classification")),
                                rs.getString("classification_note"),
                                rs.getObject("classified_at", OffsetDateTime.class)
                                        .toInstant()),
                        id)
                .stream()
                .findFirst();
    }
}
