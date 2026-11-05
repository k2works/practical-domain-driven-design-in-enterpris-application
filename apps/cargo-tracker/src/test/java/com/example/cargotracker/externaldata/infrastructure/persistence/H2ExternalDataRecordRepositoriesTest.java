package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalDataRecordRepositoriesContract;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecisionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
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

    @Autowired
    ReceivedRecordDecisionRepository receivedRecordDecisions;

    @Override
    protected ReceivedRecordDecisionRepository receivedRecordDecisions() {
        return receivedRecordDecisions;
    }

    @Override
    protected Optional<ReceivedRecordDecision> storedDecision(UUID receivedRecordId) {
        return MyBatisExternalDataRecordRepositoriesIntegrationTest.readDecision(jdbc, receivedRecordId);
    }

    @Override
    protected UUID receivedRecordWithoutIngestion() {
        return MyBatisExternalDataRecordRepositoriesIntegrationTest.insertReceivedRecordWithoutIngestion(jdbc);
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
    void 行18_H2でも外部原本と取得時刻の片方だけがNULLの隔離は制約の違反() {
        ExternalRecord externalRecord = new ExternalRecord(
                UUID.randomUUID(),
                new IdempotencyKey("H2 の提供元-" + UUID.randomUUID(), "VOYAGE", "V-101", "1"),
                PayloadHash.of(new byte[] {1}),
                PayloadObjectKey.newRecordPayload(),
                NOW.toInstant());
        externalRecords.add(externalRecord);
        UUID externalRecordId = externalRecord.id();

        assertThatThrownBy(() -> insertReceivedRecord(null, NOW, "QUARANTINED"))
                .as("外部原本だけがない隔離")
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(
                        e -> assertThat(e.getMessage()).containsIgnoringCase("ck_received_record_keyless_quarantine"));
        assertThatThrownBy(() -> insertReceivedRecord(externalRecordId, null, "QUARANTINED"))
                .as("取得時刻だけがない隔離")
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(
                        e -> assertThat(e.getMessage()).containsIgnoringCase("ck_received_record_keyless_quarantine"));
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

    /** Bolt 39c3 の行 6（受信記録の行の範囲の CHECK）。39b2 の同じ番号の行と名前が衝突しないよう入れ子にする。 */
    @Nested
    class Bolt39c3_受信記録の行の範囲 {

        @Test
        void 行6_H2でも行の範囲は2列そろってNULLかそろって1以上で始まりが終わり以下でほかは制約の違反() {
            insertKeylessQuarantine(null, null);
            insertKeylessQuarantine(3, 3);
            insertKeylessQuarantine(3, 5);

            for (Integer[] range : List.of(
                    new Integer[] {null, 3}, new Integer[] {3, null}, new Integer[] {0, 0}, new Integer[] {5, 3})) {
                assertThatThrownBy(() -> insertKeylessQuarantine(range[0], range[1]))
                        .as("行の範囲 %s〜%s", range[0], range[1])
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .satisfies(
                                e -> assertThat(e.getMessage()).containsIgnoringCase("ck_received_record_line_range"));
            }
        }

        /** 行の範囲を指定したキーのない隔離の受信記録をポートを通さず書く（record は型で拒むため）。 */
        private void insertKeylessQuarantine(Integer firstLine, Integer lastLine) {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO external_data.received_record (id, external_record_id, ingestion_id, payload_hash,"
                            + " payload_object_key, acquired_at, classification, classification_note, classified_at,"
                            + " first_line_number, last_line_number)"
                            + " VALUES (?, NULL, NULL, ?, ?, NULL, 'QUARANTINED', NULL, ?, ?, ?)",
                    id,
                    HASH,
                    "externaldata/records/" + id,
                    NOW,
                    firstLine,
                    lastLine);
        }
    }

    /** Bolt 39d の行 7（判断の表に行のある隔離）。本体は PostgreSQL のテストと共有する。39b の行 7 と名前が衝突しないよう入れ子にする。 */
    @Nested
    class Bolt39d_判断を待つ隔離 {

        @Test
        void 行7_H2でも判断の表に行のある隔離は一覧に出ず件数にも数えない() {
            MyBatisExternalDataRecordRepositoriesIntegrationTest.assertDecidedQuarantineIsNotListed(
                    jdbc, ingestions, receivedRecords);
        }
    }

    /** Bolt 39d3 の行 1（受信記録の値の 2 列の CHECK。開始準備の決定 (2)）。39b の同じ番号の行と名前が衝突しないよう入れ子にする。 */
    @Nested
    class Bolt39d3_受信記録の値 {

        private static final String SUMMARY = "JPTYO;;2026-11-19T23:00:00.000000Z|USLAX;2026-12-01T14:00:00.000000Z;";

        @Test
        void 行1_H2でも値の2列は片方だけNULLと外部原本を持たない受信記録の値が制約の違反でそろってNULLは外部原本があっても通る() {
            ExternalRecord externalRecord = new ExternalRecord(
                    UUID.randomUUID(),
                    new IdempotencyKey("H2 の提供元-" + UUID.randomUUID(), "VOYAGE", "V2611E", "1"),
                    PayloadHash.of(new byte[] {1}),
                    PayloadObjectKey.newRecordPayload(),
                    NOW.toInstant());
            externalRecords.add(externalRecord);
            UUID externalRecordId = externalRecord.id();

            insertValued(externalRecordId, NOW, "V2611E", SUMMARY);
            insertValued(externalRecordId, NOW, null, null);
            insertValued(null, null, null, null);

            for (String[] value : List.of(new String[] {"V2611E", null}, new String[] {null, SUMMARY})) {
                assertThatThrownBy(() -> insertValued(externalRecordId, NOW, value[0], value[1]))
                        .as("片方だけの値 %s・%s", value[0], value[1])
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("ck_received_record_value"));
            }
            assertThatThrownBy(() -> insertValued(null, null, "V2611E", SUMMARY))
                    .as("外部原本を持たない受信記録の値")
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("ck_received_record_value"));
        }

        /** 値の 2 列を指定した隔離の受信記録をポートを通さず書く（record は型で拒むため）。 */
        private void insertValued(UUID externalRecordId, OffsetDateTime acquiredAt, String targetKey, String summary) {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO external_data.received_record (id, external_record_id, ingestion_id, payload_hash,"
                            + " payload_object_key, acquired_at, classification, classification_note, classified_at,"
                            + " target_key, value_summary)"
                            + " VALUES (?, ?, NULL, ?, ?, ?, 'QUARANTINED', NULL, ?, ?, ?)",
                    id,
                    externalRecordId,
                    HASH,
                    "externaldata/records/" + id,
                    acquiredAt,
                    NOW,
                    targetKey,
                    summary);
        }
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
