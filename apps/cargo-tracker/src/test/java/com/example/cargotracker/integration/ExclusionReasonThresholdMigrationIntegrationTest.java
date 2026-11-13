package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 除外理由の閾値の列の分割（V20261116100000）が、既存の行を区分ごとの列に移すことを H2 と PostgreSQL で確かめる（Bolt 39i の行 27）。
 * Flyway の {@code target} で分割の手前まで流して古い形（{@code threshold} の 1 列）の行を入れ、残りを流す。試験ごとに新しい
 * データベースを作る（H2 は DDL が自動コミットで戻せないため）。理由の行は消えず、除外の候補が適合に変わらないことと、壊れた行
 * （閾値が NULL、区切りがない）は CHECK で止まり既定の値で通さないことを確かめる。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExclusionReasonThresholdMigrationIntegrationTest {

    /** 分割の 1 つ手前の版（PostgreSQL には 20261115100100 までがある。H2 は 20261115100000 まで。target は存在する版を指す）。 */
    private static String beforeSplit(String vendor) {
        return "h2".equals(vendor) ? "20261115100000" : "20261115100100";
    }

    @Autowired
    PostgreSQLContainer postgres;

    /** 試験用のデータベース。 */
    private record Database(String vendor, SingleConnectionDataSource dataSource) {
        Database(String vendor, String url, String user, String password) {
            // 1 本の接続を開いたままにする（H2 は、制約を作った接続が閉じると IN の検査が「データベースは閉じられています」で落ちる。
            // アプリは Hikari が接続を保つので起きない）
            this(vendor, new SingleConnectionDataSource(url, user, password, true));
        }

        Flyway flyway(String target) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration/common", "classpath:db/migration/" + vendor)
                    .target(target)
                    .load();
        }

        JdbcTemplate jdbc() {
            return new JdbcTemplate(dataSource);
        }
    }

    private Database freshDatabase(String vendor) throws SQLException {
        String name = "mig" + UUID.randomUUID().toString().replace("-", "");
        if ("h2".equals(vendor)) {
            return new Database(
                    vendor,
                    "jdbc:h2:mem:" + name
                            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                    "sa",
                    "");
        }
        try (Connection admin = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        String url = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name;
        return new Database(vendor, url, postgres.getUsername(), postgres.getPassword());
    }

    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-0000000039a1");
    private static final OffsetDateTime AT = OffsetDateTime.parse("2083-10-20T00:00:00Z");

    /** 候補 1 つ（親の行）を入れる。 */
    private static void insertParents(JdbcTemplate jdbc) {
        jdbc.update(
                "INSERT INTO routing.routing_case (id, case_number, transport_request_id, transport_request_number,"
                        + " transport_request_version_no, quotation_id, origin_unlocode, destination_unlocode,"
                        + " arrival_deadline, cargo_category, requested_at, version, created_at, updated_at,"
                        + " shipper_company_id) VALUES (?, 'RC-2083-9001', ?, 'TR-2083-9001', 1, ?, 'JPTYO', 'NLRTM',"
                        + " ?, 'GENERAL', ?, 0, ?, ?, ?)",
                CASE_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AT,
                AT,
                AT,
                AT,
                UUID.randomUUID());
        jdbc.update(
                "INSERT INTO routing.route_version (routing_case_id, route_version_no, status, candidates_evaluated_at,"
                        + " created_at) VALUES (?, 1, 'CANDIDATES_PRESENTED', ?, ?)",
                CASE_ID,
                AT,
                AT);
        jdbc.update(
                "INSERT INTO routing.route_candidate (routing_case_id, route_version_no, candidate_no, conforming,"
                        + " estimated_arrival_at, evaluated_at, info_insufficient) VALUES (?, 1, 1, FALSE, ?, ?, FALSE)",
                CASE_ID,
                AT,
                AT);
    }

    private static void insertOldReason(JdbcTemplate jdbc, int reasonNo, String code, String threshold) {
        jdbc.update(
                "INSERT INTO routing.exclusion_reason (routing_case_id, route_version_no, candidate_no, reason_no,"
                        + " reason_code, violated_at, threshold, info_version) VALUES (?, 1, 1, ?, ?, ?, ?, 'V@1')",
                CASE_ID,
                reasonNo,
                code,
                AT,
                threshold);
    }

    @ParameterizedTest(name = "{0}: 五つの区分の既存の行が区分ごとの列に移り、行数が同じで除外のまま")
    @ValueSource(strings = {"h2", "postgresql"})
    void 五つの区分の既存の行がそれぞれの列に移る(String vendor) throws SQLException {
        Database database = freshDatabase(vendor);
        database.flyway(beforeSplit(vendor)).migrate();
        JdbcTemplate jdbc = database.jdbc();
        insertParents(jdbc);
        insertOldReason(jdbc, 1, "DEADLINE_EXCEEDED", "2083-11-02T00:00:00Z");
        insertOldReason(jdbc, 2, "CONNECTION_TOO_SHORT", "SGSIN PT8H");
        insertOldReason(jdbc, 3, "NOT_CONNECTABLE", "HKHKG");
        // 航海番号は空白を含み得る。最後の空白で分ける（最初の空白で分けない）
        insertOldReason(jdbc, 4, "INFO_INSUFFICIENT", "IT STALE 1 PT24H");
        insertOldReason(jdbc, 5, "CARGO_NOT_SUPPORTED", "REEFER");

        database.flyway("latest").migrate();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT reason_code, threshold_deadline, threshold_port_unlocode, threshold_required_connection,"
                        + " threshold_voyage_number, threshold_freshness_tolerance, threshold_cargo_category,"
                        + " info_version FROM routing.exclusion_reason ORDER BY reason_no");
        assertThat(rows).hasSize(5);
        assertThat(rows)
                .extracting(row -> row.get("reason_code"))
                .containsExactly(
                        "DEADLINE_EXCEEDED",
                        "CONNECTION_TOO_SHORT",
                        "NOT_CONNECTABLE",
                        "INFO_INSUFFICIENT",
                        "CARGO_NOT_SUPPORTED");
        assertThat(jdbc.queryForObject(
                                "SELECT threshold_deadline FROM routing.exclusion_reason WHERE reason_no = 1",
                                OffsetDateTime.class)
                        .toInstant())
                .isEqualTo(java.time.Instant.parse("2083-11-02T00:00:00Z"));
        assertThat(rows.get(1))
                .containsEntry("threshold_port_unlocode", "SGSIN")
                .containsEntry("threshold_required_connection", "PT8H");
        assertThat(rows.get(2)).containsEntry("threshold_port_unlocode", "HKHKG");
        assertThat(rows.get(3))
                .containsEntry("threshold_voyage_number", "IT STALE 1")
                .containsEntry("threshold_freshness_tolerance", "PT24H");
        assertThat(rows.get(4)).containsEntry("threshold_cargo_category", "REEFER");
        assertThat(rows).allSatisfy(row -> assertThat(row).containsEntry("info_version", "V@1"));
        // 理由が消えて除外の候補が適合に変わらない（適合は理由が空かで決まる）
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM routing.exclusion_reason WHERE routing_case_id = ?",
                        Integer.class,
                        CASE_ID))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                        "SELECT conforming FROM routing.route_candidate WHERE routing_case_id = ?",
                        Boolean.class,
                        CASE_ID))
                .isFalse();
    }

    @ParameterizedTest(name = "{0}: 閾値が NULL の行があると CHECK で失敗して既定の値で通さない")
    @ValueSource(strings = {"h2", "postgresql"})
    void 閾値がNULLの行があると移し替えは失敗する(String vendor) throws SQLException {
        Database database = freshDatabase(vendor);
        database.flyway(beforeSplit(vendor)).migrate();
        JdbcTemplate jdbc = database.jdbc();
        insertParents(jdbc);
        insertOldReason(jdbc, 1, "NOT_CONNECTABLE", null);

        Flyway latest = database.flyway("latest");
        assertThatThrownBy(latest::migrate).isInstanceOf(FlywayException.class);
        assertOldFormRemains(vendor, jdbc, 1);
    }

    @ParameterizedTest(name = "{0}: 区切りのない接続不足と情報不足の行は CHECK で失敗して既定の値で通さない")
    @ValueSource(strings = {"h2", "postgresql"})
    void 区切りのない行があると移し替えは失敗する(String vendor) throws SQLException {
        Database database = freshDatabase(vendor);
        database.flyway(beforeSplit(vendor)).migrate();
        JdbcTemplate jdbc = database.jdbc();
        insertParents(jdbc);
        insertOldReason(jdbc, 1, "CONNECTION_TOO_SHORT", "SGSINPT8H");
        insertOldReason(jdbc, 2, "INFO_INSUFFICIENT", "PT24H");

        Flyway latest = database.flyway("latest");
        assertThatThrownBy(latest::migrate).isInstanceOf(FlywayException.class);
        assertOldFormRemains(vendor, jdbc, 2);
    }

    /**
     * 失敗した移し替えの後の形（Bolt 40-1 の M7）。どちらの DB でも Flyway の履歴に分割の成功行がない。PostgreSQL は DDL もトランザクションなので巻き戻り、
     * 移し替えの前の列と行が残り、新しい列が 1 つもない。H2 は DDL を戻せないので、半端な形が残ることは確かめず、履歴に成功行がないことだけを確かめる。
     */
    private static void assertOldFormRemains(String vendor, JdbcTemplate jdbc, int rowCount) {
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '20261116100000' AND success",
                        Integer.class))
                .isZero();
        if ("postgresql".equals(vendor)) {
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM routing.exclusion_reason WHERE threshold IS NOT NULL OR reason_code = 'NOT_CONNECTABLE'",
                            Integer.class))
                    .isEqualTo(rowCount);
            assertThat(jdbc.queryForList(
                            "SELECT column_name FROM information_schema.columns"
                                    + " WHERE table_schema = 'routing' AND table_name = 'exclusion_reason'"
                                    + " AND column_name LIKE 'threshold%' ORDER BY column_name",
                            String.class))
                    .containsExactly("threshold");
        }
    }
}
