package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateExternalRecordException;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalDataRecordRepositoriesContract;
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
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Test
    void 行12_一意制約の違反の名前付きの例外は元の例外を原因に持つ() {
        IdempotencyKey key = new IdempotencyKey("PostgreSQL の提供元-" + UUID.randomUUID(), "VOYAGE", "V-012", "1");
        externalRecords.add(externalRecord(key));
        ExternalRecord again = externalRecord(key);

        assertThatThrownBy(() -> externalRecords.add(again))
                .isInstanceOf(DuplicateExternalRecordException.class)
                .hasCauseInstanceOf(DuplicateKeyException.class);
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
                                + " classification, classification_note, classified_at, first_line_number, last_line_number"
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
                                        rs.getObject("last_line_number", Integer.class))),
                        id)
                .stream()
                .findFirst();
    }

    /** ファイルの誤りと Bolt 39c3 より前の受信記録は行の範囲が NULL（Bolt 39c3 の行 4）。 */
    private static PhysicalLineRange lineRangeOrNull(Integer first, Integer last) {
        return first == null ? null : new PhysicalLineRange(first, last);
    }

    /** キーのない隔離の取得時刻は NULL（Bolt 39b2 の行 18）。 */
    private static Instant instantOrNull(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
