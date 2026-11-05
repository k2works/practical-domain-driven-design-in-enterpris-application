package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 外部データのスキーマ（データモデル `external_data`。Bolt 39a3）。冪等性キーと情報源の単位の一意（BR-12・BR-18）、受信記録ごとに判断と採用値は
 * 1 つ、区分値と外部キーを表の制約で守る。
 *
 * <p>共有の DB にデータを残さないよう、テストごとに 1 本の接続で自動コミットを切り、最後にロールバックする（Bolt 26b P-5）。違反の後の PostgreSQL は同じ
 * トランザクションの文を受け付けないので、違反は SAVEPOINT まで戻す。情報源は {@code CANDIDATE} で入れる。行 4b だけは承認の状態の 3 値を確かめるため
 * {@code APPROVED}・{@code SUSPENDED} も入れるが、1 本の接続でロールバックするのでほかのテストからは見えない（承認済みの情報源の件数を数える ED-INV-01 の
 * テストを汚さない）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExternalDataSchemaIntegrationTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String NOT_NULL_VIOLATION = "23502";
    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-11-16T09:00:00Z"));
    private static final String HASH = "0".repeat(64);

    @Autowired
    DataSource dataSource;

    @Test
    void 行3_同じ冪等性キーの外部原本は一つだけ() throws SQLException {
        inRolledBackTransaction(connection -> {
            String provider = newProvider();
            insertExternalRecord(connection, provider, "1");

            assertViolation(connection, () -> insertExternalRecord(connection, provider, "1"), UNIQUE_VIOLATION);
            insertExternalRecord(connection, provider, "2");
        });
    }

    @Test
    void 行3_同じ提供元と原本種別の情報源は一つだけ() throws SQLException {
        inRolledBackTransaction(connection -> {
            String provider = newProvider();
            insertDataSource(connection, provider, "VOYAGE_SCHEDULE", "CANDIDATE");

            assertViolation(
                    connection,
                    () -> insertDataSource(connection, provider, "VOYAGE_SCHEDULE", "CANDIDATE"),
                    UNIQUE_VIOLATION);
            insertDataSource(connection, provider, "MILESTONE", "CANDIDATE");
        });
    }

    @Test
    void 行4_情報源の承認の状態は決めた値だけ() throws SQLException {
        inRolledBackTransaction(connection -> assertViolation(
                connection,
                () -> insertDataSource(connection, newProvider(), "VOYAGE_SCHEDULE", "REJECTED"),
                CHECK_VIOLATION));
    }

    @Test
    void 行4_受信記録の分類は決めた値だけ() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID externalRecordId = insertExternalRecord(connection, newProvider(), "1");

            assertViolation(
                    connection,
                    () -> insertReceivedRecord(connection, externalRecordId, null, "ACCEPTED"),
                    CHECK_VIOLATION);
        });
    }

    @Test
    void 行4_受信記録の判断は決めた値だけ() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID receivedRecordId = insertReceivedRecord(connection);

            assertViolation(
                    connection, () -> insertDecision(connection, receivedRecordId, "APPROVED"), CHECK_VIOLATION);
        });
    }

    @Test
    void 行4_採用値の対象は決めた値だけ() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID receivedRecordId = insertReceivedRecord(connection);

            assertViolation(
                    connection, () -> insertAdoptedValue(connection, receivedRecordId, "PORT"), CHECK_VIOLATION);
        });
    }

    @Test
    void 行4b_決めた値のすべてとNULLを許す列のNULLが入る() throws SQLException {
        inRolledBackTransaction(connection -> {
            String provider = newProvider();
            UUID dataSourceId = insertDataSource(connection, provider, "CANDIDATE_TYPE", "CANDIDATE");
            insertDataSource(connection, provider, "APPROVED_TYPE", "APPROVED");
            insertDataSource(connection, provider, "SUSPENDED_TYPE", "SUSPENDED");
            UUID ingestionId = insertIngestion(connection, dataSourceId);
            UUID externalRecordId = insertExternalRecord(connection, provider, "1");

            UUID adopted = insertReceivedRecord(connection, externalRecordId, ingestionId, "ADOPTED");
            insertReceivedRecord(connection, externalRecordId, null, "DUPLICATE");
            UUID quarantined = insertReceivedRecord(connection, externalRecordId, null, "QUARANTINED");
            UUID discrepancy = insertReceivedRecord(connection, externalRecordId, null, "DISCREPANCY");
            insertDecision(connection, quarantined, "ADOPTED");
            insertDecision(connection, discrepancy, "REJECTED");
            // 採用値は採用の分類の受信記録と、採用と判断した受信記録に付ける（却下した受信記録には付けない。整合は Bolt 39d のサービスが守る）
            insertAdoptedValue(connection, adopted, "MILESTONE");
            insertAdoptedValue(connection, quarantined, "VOYAGE");

            assertThat(count(
                            connection,
                            "SELECT count(*) FROM external_data.data_source WHERE provider = ?"
                                    + " AND approved_by IS NULL AND approved_at IS NULL",
                            provider))
                    .isEqualTo(3);
            assertThat(count(
                            connection,
                            "SELECT count(*) FROM external_data.received_record WHERE external_record_id = ?"
                                    + " AND classification_note IS NULL",
                            externalRecordId))
                    .isEqualTo(4);
            assertThat(count(
                            connection,
                            "SELECT count(*) FROM external_data.received_record WHERE external_record_id = ?"
                                    + " AND ingestion_id IS NULL",
                            externalRecordId))
                    .isEqualTo(3);
            assertThat(count(
                            connection,
                            "SELECT count(*) FROM external_data.received_record_decision d"
                                    + " JOIN external_data.received_record r ON r.id = d.received_record_id"
                                    + " WHERE r.external_record_id = ? AND d.reason IS NULL",
                            externalRecordId))
                    .isEqualTo(2);
            assertThat(count(
                            connection,
                            "SELECT count(*) FROM external_data.adopted_value a"
                                    + " JOIN external_data.received_record r ON r.id = a.received_record_id"
                                    + " WHERE r.external_record_id = ?",
                            externalRecordId))
                    .isEqualTo(2);
        });
    }

    @Test
    void 行5_受信記録ごとに判断は一つ() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID receivedRecordId = insertReceivedRecord(connection);
            insertDecision(connection, receivedRecordId, "ADOPTED");

            assertViolation(
                    connection, () -> insertDecision(connection, receivedRecordId, "REJECTED"), UNIQUE_VIOLATION);
        });
    }

    @Test
    void 行5_受信記録ごとに採用値は一つ() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID receivedRecordId = insertReceivedRecord(connection);
            insertAdoptedValue(connection, receivedRecordId, "VOYAGE");

            assertViolation(
                    connection, () -> insertAdoptedValue(connection, receivedRecordId, "VOYAGE"), UNIQUE_VIOLATION);
        });
    }

    @Test
    void 行6_外部キーの先がない行は入らない() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID externalRecordId = insertExternalRecord(connection, newProvider(), "1");

            assertViolation(connection, () -> insertIngestion(connection, UUID.randomUUID()), FOREIGN_KEY_VIOLATION);
            assertViolation(
                    connection,
                    () -> insertReceivedRecord(connection, UUID.randomUUID(), null, "ADOPTED"),
                    FOREIGN_KEY_VIOLATION);
            assertViolation(
                    connection,
                    () -> insertReceivedRecord(connection, externalRecordId, UUID.randomUUID(), "ADOPTED"),
                    FOREIGN_KEY_VIOLATION);
            assertViolation(
                    connection, () -> insertDecision(connection, UUID.randomUUID(), "ADOPTED"), FOREIGN_KEY_VIOLATION);
            assertViolation(
                    connection,
                    () -> insertAdoptedValue(connection, UUID.randomUUID(), "VOYAGE"),
                    FOREIGN_KEY_VIOLATION);
        });
    }

    @Test
    void 行18_外部原本か取得時刻がNULLの受信記録は隔離のほかはキーのない隔離のCHECKの違反() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID externalRecordId = insertExternalRecord(connection, newProvider(), "1");

            insertReceivedRecord(connection, null, null, null, "QUARANTINED");
            for (String classification : new String[] {"ADOPTED", "DUPLICATE", "DISCREPANCY"}) {
                assertViolation(
                        connection,
                        () -> insertReceivedRecord(connection, null, null, NOW, classification),
                        CHECK_VIOLATION);
                assertViolation(
                        connection,
                        () -> insertReceivedRecord(connection, externalRecordId, null, null, classification),
                        CHECK_VIOLATION);
            }
            assertThat(constraintExists(connection, "received_record", "ck_received_record_keyless_quarantine"))
                    .isTrue();
        });
    }

    @Test
    void 行18_外部原本と取得時刻の片方だけがNULLの隔離はキーのない隔離のCHECKの違反() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID externalRecordId = insertExternalRecord(connection, newProvider(), "1");

            assertViolation(
                    connection,
                    () -> insertReceivedRecord(connection, null, null, NOW, "QUARANTINED"),
                    CHECK_VIOLATION);
            assertViolation(
                    connection,
                    () -> insertReceivedRecord(connection, externalRecordId, null, null, "QUARANTINED"),
                    CHECK_VIOLATION);
        });
    }

    @Test
    void 行19_取込の取り込めなかった行の始まりと数の組み合わせはCHECKで守り列を書かない行は数0() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID dataSourceId = insertDataSource(connection, newProvider(), "VOYAGE_SCHEDULE", "CANDIDATE");
            UUID existing = insertIngestion(connection, dataSourceId);

            assertThat(count(
                            connection,
                            "SELECT count(*) FROM external_data.ingestion WHERE id = ?"
                                    + " AND unparsed_from_line IS NULL AND unparsed_line_count = 0",
                            existing))
                    .isEqualTo(1);
            insertIngestion(connection, dataSourceId, 12, 29);
            assertViolation(connection, () -> insertIngestion(connection, dataSourceId, null, 3), CHECK_VIOLATION);
            assertViolation(connection, () -> insertIngestion(connection, dataSourceId, 2, 0), CHECK_VIOLATION);
            assertViolation(connection, () -> insertIngestion(connection, dataSourceId, null, -1), CHECK_VIOLATION);
            assertThat(constraintExists(connection, "ingestion", "ck_ingestion_unparsed_lines"))
                    .isTrue();
        });
    }

    /** Bolt 39c3 の行 6（受信記録の行の範囲の 2 列と CHECK。開始準備の決定 (6)）。39a3 の同じ番号の行と名前が衝突しないよう入れ子にする。 */
    @Nested
    class Bolt39c3_受信記録の行の範囲 {

        @Test
        void 行6_行の範囲は2列そろってNULLかそろって1以上で始まりが終わり以下でほかはCHECKの違反() throws SQLException {
            inRolledBackTransaction(connection -> {
                insertKeylessQuarantine(connection, null, null);
                insertKeylessQuarantine(connection, 3, 3);
                insertKeylessQuarantine(connection, 3, 5);

                assertViolation(connection, () -> insertKeylessQuarantine(connection, null, 3), CHECK_VIOLATION);
                assertViolation(connection, () -> insertKeylessQuarantine(connection, 3, null), CHECK_VIOLATION);
                assertViolation(connection, () -> insertKeylessQuarantine(connection, 0, 0), CHECK_VIOLATION);
                assertViolation(connection, () -> insertKeylessQuarantine(connection, 5, 3), CHECK_VIOLATION);
                assertThat(constraintExists(connection, "received_record", "ck_received_record_line_range"))
                        .isTrue();
            });
        }

        /** 行の範囲を指定したキーのない隔離の受信記録をポートを通さず書く（record は型で拒むため）。 */
        private static void insertKeylessQuarantine(Connection connection, Integer firstLine, Integer lastLine)
                throws SQLException {
            UUID id = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO external_data.received_record"
                    + " (id, external_record_id, ingestion_id, payload_hash, payload_object_key, acquired_at,"
                    + " classification, classification_note, classified_at, first_line_number, last_line_number)"
                    + " VALUES (?, NULL, NULL, ?, ?, NULL, 'QUARANTINED', NULL, ?, ?, ?)")) {
                statement.setObject(1, id);
                statement.setString(2, HASH);
                statement.setString(3, "record/" + id);
                statement.setTimestamp(4, NOW);
                statement.setObject(5, firstLine, Types.INTEGER);
                statement.setObject(6, lastLine, Types.INTEGER);
                statement.executeUpdate();
            }
        }
    }

    /** Bolt 39d3 の行 1（受信記録の値の 2 列と CHECK と外部原本 ID の索引。開始準備の決定 (2)）。39b の同じ番号の行と名前が衝突しないよう入れ子にする。 */
    @Nested
    class Bolt39d3_受信記録の値 {

        private static final String SUMMARY = "JPTYO;;2026-11-19T23:00:00.000000Z|USLAX;2026-12-01T14:00:00.000000Z;";

        @Test
        void 行1_値の2列は片方だけNULLと外部原本を持たない受信記録の値がCHECKの違反でそろってNULLは外部原本があっても通る() throws SQLException {
            inRolledBackTransaction(connection -> {
                UUID externalRecordId = insertExternalRecord(connection, newProvider(), "1");

                insertValued(connection, externalRecordId, "V2611E", SUMMARY);
                insertValued(connection, externalRecordId, null, null);
                insertValued(connection, null, null, null);

                assertViolation(
                        connection, () -> insertValued(connection, externalRecordId, "V2611E", null), CHECK_VIOLATION);
                assertViolation(
                        connection, () -> insertValued(connection, externalRecordId, null, SUMMARY), CHECK_VIOLATION);
                assertViolation(connection, () -> insertValued(connection, null, "V2611E", SUMMARY), CHECK_VIOLATION);
                assertThat(constraintExists(connection, "received_record", "ck_received_record_value"))
                        .isTrue();
                assertThat(count(
                                connection,
                                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'external_data'"
                                        + " AND tablename = 'received_record' AND indexname = ?",
                                "ix_received_record_external_record"))
                        .isEqualTo(1);
            });
        }

        /** 値の 2 列を指定した隔離の受信記録をポートを通さず書く（外部原本がなければ取得時刻も NULL。record は型で拒むため）。 */
        private static void insertValued(Connection connection, UUID externalRecordId, String targetKey, String summary)
                throws SQLException {
            UUID id = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO external_data.received_record"
                    + " (id, external_record_id, ingestion_id, payload_hash, payload_object_key, acquired_at,"
                    + " classification, classification_note, classified_at, target_key, value_summary)"
                    + " VALUES (?, ?, NULL, ?, ?, ?, 'QUARANTINED', NULL, ?, ?, ?)")) {
                statement.setObject(1, id);
                statement.setObject(2, externalRecordId);
                statement.setString(3, HASH);
                statement.setString(4, "record/" + id);
                statement.setTimestamp(5, externalRecordId == null ? null : NOW);
                statement.setTimestamp(6, NOW);
                statement.setString(7, targetKey);
                statement.setString(8, summary);
                statement.executeUpdate();
            }
        }
    }

    @Test
    void 受信記録の分類の時刻は必須() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID externalRecordId = insertExternalRecord(connection, newProvider(), "1");

            assertViolation(
                    connection,
                    () -> {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO external_data.received_record"
                                        + " (id, external_record_id, payload_hash, payload_object_key, acquired_at,"
                                        + " classification, classified_at) VALUES (?, ?, ?, 'record/x', ?, 'ADOPTED', NULL)")) {
                            statement.setObject(1, UUID.randomUUID());
                            statement.setObject(2, externalRecordId);
                            statement.setString(3, HASH);
                            statement.setTimestamp(4, NOW);
                            statement.executeUpdate();
                        }
                    },
                    NOT_NULL_VIOLATION);
        });
    }

    @Test
    void 受信記録の判断の判断者は必須() throws SQLException {
        inRolledBackTransaction(connection -> {
            UUID receivedRecordId = insertReceivedRecord(connection);

            assertViolation(
                    connection,
                    () -> {
                        try (PreparedStatement statement =
                                connection.prepareStatement("INSERT INTO external_data.received_record_decision"
                                        + " (id, received_record_id, decision, decided_by, decided_at)"
                                        + " VALUES (?, ?, 'ADOPTED', NULL, ?)")) {
                            statement.setObject(1, UUID.randomUUID());
                            statement.setObject(2, receivedRecordId);
                            statement.setTimestamp(3, NOW);
                            statement.executeUpdate();
                        }
                    },
                    NOT_NULL_VIOLATION);
        });
    }

    /** 冪等性キーと情報源の単位の一意制約に、ほかのテストの行とぶつからないよう、テストごとに新しい提供元にする。 */
    private static String newProvider() {
        return "PROVIDER-" + UUID.randomUUID();
    }

    private UUID insertReceivedRecord(Connection connection) throws SQLException {
        UUID externalRecordId = insertExternalRecord(connection, newProvider(), "1");
        return insertReceivedRecord(connection, externalRecordId, null, "QUARANTINED");
    }

    private static UUID insertDataSource(Connection connection, String provider, String recordType, String status)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO external_data.data_source"
                + " (id, provider, record_type, approval_status, approved_by, approved_at, version)"
                + " VALUES (?, ?, ?, ?, NULL, NULL, 0)")) {
            statement.setObject(1, id);
            statement.setString(2, provider);
            statement.setString(3, recordType);
            statement.setString(4, status);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertIngestion(Connection connection, UUID dataSourceId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO external_data.ingestion"
                + " (id, data_source_id, file_object_key, file_hash, ingested_by, ingested_at, received_count)"
                + " VALUES (?, ?, ?, ?, ?, ?, 0)")) {
            statement.setObject(1, id);
            statement.setObject(2, dataSourceId);
            statement.setString(3, "ingestion/" + id);
            statement.setString(4, HASH);
            statement.setObject(5, UUID.randomUUID());
            statement.setTimestamp(6, NOW);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertIngestion(
            Connection connection, UUID dataSourceId, Integer unparsedFromLine, int unparsedLineCount)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO external_data.ingestion"
                + " (id, data_source_id, file_object_key, file_hash, ingested_by, ingested_at, received_count,"
                + " unparsed_from_line, unparsed_line_count) VALUES (?, ?, ?, ?, ?, ?, 40, ?, ?)")) {
            statement.setObject(1, id);
            statement.setObject(2, dataSourceId);
            statement.setString(3, "ingestion/" + id);
            statement.setString(4, HASH);
            statement.setObject(5, UUID.randomUUID());
            statement.setTimestamp(6, NOW);
            statement.setObject(7, unparsedFromLine, Types.INTEGER);
            statement.setInt(8, unparsedLineCount);
            statement.executeUpdate();
        }
        return id;
    }

    private static boolean constraintExists(Connection connection, String table, String constraint)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM"
                + " information_schema.table_constraints WHERE table_schema = 'external_data' AND table_name = ?"
                + " AND constraint_name = ? AND constraint_type = 'CHECK'")) {
            statement.setString(1, table);
            statement.setString(2, constraint);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private static UUID insertExternalRecord(Connection connection, String provider, String externalVersion)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO external_data.external_record"
                + " (id, provider, record_type, external_id, external_version, payload_hash, payload_object_key,"
                + " first_received_at) VALUES (?, ?, 'VOYAGE_SCHEDULE', 'V-0001', ?, ?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, provider);
            statement.setString(3, externalVersion);
            statement.setString(4, HASH);
            statement.setString(5, "record/" + id);
            statement.setTimestamp(6, NOW);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertReceivedRecord(
            Connection connection, UUID externalRecordId, UUID ingestionId, String classification) throws SQLException {
        return insertReceivedRecord(connection, externalRecordId, ingestionId, NOW, classification);
    }

    private static UUID insertReceivedRecord(
            Connection connection, UUID externalRecordId, UUID ingestionId, Timestamp acquiredAt, String classification)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO external_data.received_record"
                + " (id, external_record_id, ingestion_id, payload_hash, payload_object_key, acquired_at,"
                + " classification, classification_note, classified_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)")) {
            statement.setObject(1, id);
            statement.setObject(2, externalRecordId);
            statement.setObject(3, ingestionId);
            statement.setString(4, HASH);
            statement.setString(5, "record/" + id);
            statement.setTimestamp(6, acquiredAt);
            statement.setString(7, classification);
            statement.setTimestamp(8, NOW);
            statement.executeUpdate();
        }
        return id;
    }

    private static void insertDecision(Connection connection, UUID receivedRecordId, String decision)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("INSERT INTO external_data.received_record_decision"
                        + " (id, received_record_id, decision, decided_by, decided_at, reason)"
                        + " VALUES (?, ?, ?, ?, ?, NULL)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, receivedRecordId);
            statement.setString(3, decision);
            statement.setObject(4, UUID.randomUUID());
            statement.setTimestamp(5, NOW);
            statement.executeUpdate();
        }
    }

    private static void insertAdoptedValue(Connection connection, UUID receivedRecordId, String targetType)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO external_data.adopted_value"
                + " (id, received_record_id, target_type, target_key, value_summary, info_version, published_at)"
                + " VALUES (?, ?, ?, 'V-0001', '運航予定', '1', ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, receivedRecordId);
            statement.setString(3, targetType);
            statement.setTimestamp(4, NOW);
            statement.executeUpdate();
        }
    }

    private static int count(Connection connection, String sql, Object key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, key);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void inRolledBackTransaction(ConnectionAction action) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                action.run(connection);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }

    private static void assertViolation(Connection connection, SqlAction action, String sqlState) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(sqlState));
        connection.rollback(savepoint);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface ConnectionAction {
        void run(Connection connection) throws SQLException;
    }
}
