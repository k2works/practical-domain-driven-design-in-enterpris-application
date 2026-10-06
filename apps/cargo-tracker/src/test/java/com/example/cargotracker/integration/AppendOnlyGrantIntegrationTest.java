package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 追記専用の表を PostgreSQL の権限で守る（データモデル「追記専用」、運用要件 DA-02、ADR-007）。
 * 追記専用の表は、表の定義に印（COMMENT ON TABLE ... IS '<日本語名> [append-only]'）を付け、afterMigrate のコールバックが
 * 印の付いた表から UPDATE・DELETE を外す。印の付け忘れと外し忘れを、表の集合の一致で検出する。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AppendOnlyGrantIntegrationTest {

    /** データモデルの「追記専用」の表のうち、作成済みのもの。表を足したらここにも足す。 */
    static final Set<String> APPEND_ONLY_TABLES = Set.of(
            "quotation.transport_request_version",
            "quotation.review_record",
            "quotation.required_document",
            "identity.audit_record");

    private static final String APP_PASSWORD = "cargo_tracker_app_test";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Autowired
    DataSource dataSource;

    @Value("${spring.flyway.placeholders.appuser}")
    String appUser;

    private UUID transportRequestId;

    private Connection connectAsApplicationUser() throws SQLException {
        String url;
        try (Connection owner = dataSource.getConnection()) {
            url = owner.getMetaData().getURL();
        }
        return DriverManager.getConnection(url, appUser, APP_PASSWORD);
    }

    @BeforeEach
    void アプリケーション利用者で輸送要求と版を追加する() throws SQLException {
        transportRequestId = UUID.randomUUID();
        try (Connection connection = connectAsApplicationUser();
                PreparedStatement request = connection.prepareStatement("INSERT INTO quotation.transport_request"
                        + " (id, request_number, shipper_company_id, status, current_version_no, version)"
                        + " VALUES (?, ?, ?, 'UNDER_REVIEW', 1, 0)");
                PreparedStatement version = connection.prepareStatement(
                        "INSERT INTO quotation.transport_request_version"
                                + " (transport_request_id, version_no, consignee_company_id, origin_unlocode,"
                                + " destination_unlocode, arrival_deadline, cargo_category, package_type, package_count,"
                                + " gross_weight_kg, volume_m3, submitted_by, submitted_at)"
                                + " VALUES (?, 1, ?, 'JPTYO', 'NLRTM', ?, 'GENERAL', 'PALLET', 12, 8400, 32.5, ?, ?)")) {
            request.setObject(1, transportRequestId);
            // 業務番号の一意制約にほかのテストの番号とぶつからないよう、輸送要求ごとに別の連番（形式どおりの 8 桁）にする
            request.setString(2, "TR-2099-" + (10_000_000 + Math.floorMod(transportRequestId.hashCode(), 90_000_000)));
            request.setObject(3, UUID.randomUUID());
            request.executeUpdate();
            version.setObject(1, transportRequestId);
            version.setObject(2, UUID.randomUUID());
            version.setTimestamp(3, Timestamp.from(Instant.parse("2026-11-02T00:00:00Z")));
            version.setObject(4, UUID.randomUUID());
            version.setTimestamp(5, Timestamp.from(Instant.parse("2026-10-05T01:00:00Z")));
            version.executeUpdate();
        }
    }

    @Test
    void 追記専用の印の付いた表とアプリケーション利用者が更新できない表が一致する() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(appendOnlyMarkedTables(connection)).isEqualTo(APPEND_ONLY_TABLES);
            assertThat(tablesWithoutUpdatePrivilege(connection)).isEqualTo(APPEND_ONLY_TABLES);
        }
    }

    @Test
    void アプリケーション利用者は輸送要求版を読める() throws SQLException {
        try (Connection connection = connectAsApplicationUser();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM quotation.transport_request_version WHERE transport_request_id = ?")) {
            statement.setObject(1, transportRequestId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void アプリケーション利用者は輸送要求版を更新できない() throws SQLException {
        assertInsufficientPrivilege("UPDATE quotation.transport_request_version SET origin_unlocode = 'CNSHA'"
                + " WHERE transport_request_id = ?");
    }

    @Test
    void アプリケーション利用者は輸送要求版を削除できない() throws SQLException {
        assertInsufficientPrivilege("DELETE FROM quotation.transport_request_version WHERE transport_request_id = ?");
    }

    @Test
    void アプリケーション利用者は追記専用でない輸送要求を更新できる() throws SQLException {
        try (Connection connection = connectAsApplicationUser();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE quotation.transport_request SET version = 1 WHERE id = ?")) {
            statement.setObject(1, transportRequestId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    @Test
    void アプリケーション利用者は業務番号の採番の表を作って更新できる() throws SQLException {
        try (Connection connection = connectAsApplicationUser();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO quotation.transport_request_number_counter (number_year, last_no) VALUES (2098, 0)");
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE quotation.transport_request_number_counter SET last_no = last_no + 1 WHERE number_year = 2098")) {
            insert.executeUpdate();
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
    }

    @Test
    void アプリケーション利用者は審査記録を追加できるが更新も削除もできない() throws SQLException {
        try (Connection connection = connectAsApplicationUser();
                PreparedStatement insert = connection.prepareStatement("INSERT INTO quotation.review_record"
                        + " (id, transport_request_id, version_no, decision, reviewer_id, rationale, decided_at)"
                        + " VALUES (?, ?, 1, 'APPROVED', ?, '確認した', ?)")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, transportRequestId);
            insert.setObject(3, UUID.randomUUID());
            insert.setTimestamp(4, Timestamp.from(Instant.parse("2026-10-05T03:00:00Z")));
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }
        assertInsufficientPrivilege(
                "UPDATE quotation.review_record SET rationale = '書き換え' WHERE transport_request_id = ?");
        assertInsufficientPrivilege("DELETE FROM quotation.review_record WHERE transport_request_id = ?");
    }

    private void assertInsufficientPrivilege(String sql) throws SQLException {
        try (Connection connection = connectAsApplicationUser();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, transportRequestId);
            assertThatThrownBy(statement::executeUpdate)
                    .isInstanceOfSatisfying(
                            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
        }
    }

    private static Set<String> appendOnlyMarkedTables(Connection connection) throws SQLException {
        return tables(
                connection,
                "SELECT n.nspname || '.' || c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE c.relkind = 'r' AND obj_description(c.oid, 'pg_class') LIKE '% [append-only]'");
    }

    private Set<String> tablesWithoutUpdatePrivilege(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT table_schema || '.' || table_name"
                + " FROM information_schema.tables WHERE table_schema IN ('quotation', 'identity', 'platform')"
                + " AND table_type = 'BASE TABLE'"
                + " AND NOT has_table_privilege(?, table_schema || '.' || table_name, 'UPDATE')")) {
            statement.setString(1, appUser);
            return collect(statement.executeQuery());
        }
    }

    private static Set<String> tables(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            return collect(statement.executeQuery());
        }
    }

    private static Set<String> collect(ResultSet result) throws SQLException {
        try (result) {
            Set<String> names = new HashSet<>();
            while (result.next()) {
                names.add(result.getString(1));
            }
            return names;
        }
    }
}
