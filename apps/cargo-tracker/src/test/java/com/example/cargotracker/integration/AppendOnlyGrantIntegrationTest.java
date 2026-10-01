package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 追記専用の表を PostgreSQL の権限で守る（データモデル、運用要件 DA-02、ADR-007）。
 * アプリケーション利用者は、追記専用の表に INSERT・SELECT だけができ、UPDATE・DELETE は拒否される。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AppendOnlyGrantIntegrationTest {

    private static final String APP_USER = "cargo_tracker_app";
    private static final String APP_PASSWORD = "cargo_tracker_app_test";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Autowired
    DataSource dataSource;

    private UUID transportRequestId;

    private Connection connectAsApplicationUser() throws SQLException {
        String url = ((HikariDataSource) dataSource).getJdbcUrl();
        return DriverManager.getConnection(url, APP_USER, APP_PASSWORD);
    }

    @BeforeEach
    void アプリケーション利用者で輸送要求と版を追加する() throws SQLException {
        transportRequestId = UUID.randomUUID();
        try (Connection connection = connectAsApplicationUser();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO quotation.transport_request"
                    + " (id, shipper_company_id, status, current_version_no, version) VALUES ('"
                    + transportRequestId + "', '" + UUID.randomUUID() + "', 'UNDER_REVIEW', 1, 0)");
            statement.executeUpdate("INSERT INTO quotation.transport_request_version"
                    + " (transport_request_id, version_no, origin_unlocode, destination_unlocode, submitted_by,"
                    + " submitted_at) VALUES ('" + transportRequestId + "', 1, 'JPTYO', 'NLRTM', '"
                    + UUID.randomUUID() + "', now())");
        }
    }

    @Test
    void アプリケーション利用者は輸送要求版を読める() throws SQLException {
        try (Connection connection = connectAsApplicationUser();
                Statement statement = connection.createStatement()) {
            assertThat(statement
                            .executeQuery("SELECT count(*) FROM quotation.transport_request_version"
                                    + " WHERE transport_request_id = '" + transportRequestId + "'")
                            .next())
                    .isTrue();
        }
    }

    @Test
    void アプリケーション利用者は輸送要求版を更新できない() throws SQLException {
        try (Connection connection = connectAsApplicationUser();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE quotation.transport_request_version"
                            + " SET origin_unlocode = 'CNSHA' WHERE transport_request_id = '" + transportRequestId
                            + "'"))
                    .isInstanceOfSatisfying(
                            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
        }
    }

    @Test
    void アプリケーション利用者は輸送要求版を削除できない() throws SQLException {
        try (Connection connection = connectAsApplicationUser();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM quotation.transport_request_version"
                            + " WHERE transport_request_id = '" + transportRequestId + "'"))
                    .isInstanceOfSatisfying(
                            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
        }
    }

    @Test
    void アプリケーション利用者は追記専用でない輸送要求を更新できる() throws SQLException {
        try (Connection connection = connectAsApplicationUser();
                Statement statement = connection.createStatement()) {
            assertThat(statement.executeUpdate("UPDATE quotation.transport_request SET version = 1 WHERE id = '"
                            + transportRequestId + "'"))
                    .isEqualTo(1);
        }
    }
}
