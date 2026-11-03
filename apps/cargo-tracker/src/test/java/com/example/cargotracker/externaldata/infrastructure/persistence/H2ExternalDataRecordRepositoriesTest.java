package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalDataRecordRepositoriesContract;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 取込・外部原本・受信記録のリポジトリの契約を、ローカル起動と同じ dev プロファイルの H2 で確かめる（Bolt 39b。マッパーの SQL が H2 と
 * PostgreSQL の共通の構文で動くこと。ADR-007）。H2 は権限で追記専用を守れないので、ポートに更新・削除のメソッドを持たせない。
 * テストのトランザクションは契約のクラスの {@code @Transactional} で開き、書いた行はロールバックで消える。
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2ExternalDataRecordRepositoriesTest extends ExternalDataRecordRepositoriesContract {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-03T01:02:03Z");

    private static final String HASH = "0".repeat(64);

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
        MyBatisExternalDataRecordRepositoriesIntegrationTest.insertDataSource(jdbc, source);
    }

    @Override
    protected Optional<Ingestion> storedIngestion(UUID id) {
        return MyBatisExternalDataRecordRepositoriesIntegrationTest.readIngestion(jdbc, id);
    }

    @Override
    protected Optional<ReceivedRecord> storedReceivedRecord(UUID id) {
        return MyBatisExternalDataRecordRepositoriesIntegrationTest.readReceivedRecord(jdbc, id);
    }

    @Test
    void 行18_H2でも外部原本か取得時刻がNULLの受信記録は隔離のほかは制約の違反() {
        insertReceivedRecord(null, null, "QUARANTINED");

        for (String classification : List.of("ADOPTED", "DUPLICATE", "DISCREPANCY")) {
            assertThatThrownBy(() -> insertReceivedRecord(null, NOW, classification))
                    .as("外部原本のない %s", classification)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e ->
                            assertThat(e.getMessage()).containsIgnoringCase("ck_received_record_keyless_quarantine"));
        }
    }

    @Test
    void 行19_H2でも取り込めなかった行の始まりと数の組み合わせが規則に反すれば制約の違反() {
        UUID dataSourceId = UUID.randomUUID();
        MyBatisExternalDataRecordRepositoriesIntegrationTest.insertDataSource(
                jdbc, new DataSource(dataSourceId, "H2 の提供元-" + UUID.randomUUID(), "VOYAGE", ApprovalStatus.CANDIDATE));
        UUID existing = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.ingestion"
                        + " (id, data_source_id, file_object_key, file_hash, ingested_by, ingested_at, received_count)"
                        + " VALUES (?, ?, ?, ?, ?, ?, 0)",
                existing,
                dataSourceId,
                "externaldata/ingestions/" + existing,
                HASH,
                UUID.randomUUID(),
                NOW);

        assertThat(jdbc.queryForObject(
                        "SELECT unparsed_line_count FROM external_data.ingestion"
                                + " WHERE id = ? AND unparsed_from_line IS NULL",
                        Integer.class,
                        existing))
                .as("列を書かない行（既存の行）は始まりが NULL で数が 0")
                .isZero();
        assertThatThrownBy(() -> insertIngestion(dataSourceId, null, 3))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("ck_ingestion_unparsed_lines"));
        assertThatThrownBy(() -> insertIngestion(dataSourceId, 2, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("ck_ingestion_unparsed_lines"));
    }

    private void insertReceivedRecord(UUID externalRecordId, OffsetDateTime acquiredAt, String classification) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.received_record (id, external_record_id, ingestion_id, payload_hash,"
                        + " payload_object_key, acquired_at, classification, classification_note, classified_at)"
                        + " VALUES (?, ?, NULL, ?, ?, ?, ?, NULL, ?)",
                id,
                externalRecordId,
                HASH,
                "externaldata/records/" + id,
                acquiredAt,
                classification,
                NOW);
    }

    private void insertIngestion(UUID dataSourceId, Integer unparsedFromLine, int unparsedLineCount) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.ingestion (id, data_source_id, file_object_key, file_hash, ingested_by,"
                        + " ingested_at, received_count, unparsed_from_line, unparsed_line_count)"
                        + " VALUES (?, ?, ?, ?, ?, ?, 3, ?, ?)",
                id,
                dataSourceId,
                "externaldata/ingestions/" + id,
                HASH,
                UUID.randomUUID(),
                NOW,
                unparsedFromLine,
                unparsedLineCount);
    }
}
