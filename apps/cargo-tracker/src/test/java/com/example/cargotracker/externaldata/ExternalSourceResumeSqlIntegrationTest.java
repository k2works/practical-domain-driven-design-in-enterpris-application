package com.example.cargotracker.externaldata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 停止中の情報源を承認済みに戻す運用の SQL（{@code ops/sql/external_source_resume.sql}。W10 の持ち越し H-2。運用タスク
 * {@code external-source:resume}。台帳 D-102、W7 の議題）を、本物の PostgreSQL に流して確かめる。状態だけを変えて監査記録を残さない
 * 変更にしないよう、状態の変更と監査記録を 1 つのトランザクションにする。共有の DB を壊さないよう、トランザクションで流してロールバックする。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExternalSourceResumeSqlIntegrationTest {

    private static final Path SCRIPT = Path.of("..", "..", "ops", "sql", "external_source_resume.sql");

    @Autowired
    DataSource dataSource;

    private static String script(UUID sourceId, String reason) throws Exception {
        return Files.readString(SCRIPT, StandardCharsets.UTF_8)
                .replace(":'source_id'", "'" + sourceId + "'")
                .replace(":'reason'", "'" + reason.replace("'", "''") + "'")
                .replaceAll("(?m)^BEGIN;\\s*$", "")
                .replaceAll("(?m)^COMMIT;\\s*$", "");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String query(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private UUID source(Connection connection, String status) throws SQLException {
        UUID id = UUID.randomUUID();
        execute(
                connection,
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, approved_by, approved_at, version)"
                        + " VALUES ('" + id + "', 'テスト提供元 " + id + "', 'VOYAGE', '" + status + "',"
                        + " '00000000-0000-0000-0000-000000000999', CURRENT_TIMESTAMP, 3)");
        return id;
    }

    @Test
    void 停止中の情報源を承認済みに戻し_版を上げ_監査記録を残す() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            UUID id = source(connection, "SUSPENDED");

            execute(connection, script(id, "提供元の障害が復旧した（連絡: 2026-11-16）"));

            assertThat(query(connection, "SELECT approval_status FROM external_data.data_source WHERE id = ?", id))
                    .isEqualTo("APPROVED");
            assertThat(query(connection, "SELECT version FROM external_data.data_source WHERE id = ?", id))
                    .isEqualTo("4");
            assertThat(query(
                            connection,
                            "SELECT COUNT(*) FROM identity.audit_record WHERE action = 'DATA_SOURCE_RESUMED'"
                                    + " AND target_type = 'DATA_SOURCE' AND target_id = ? AND target_version = '4'"
                                    + " AND before_state = 'SUSPENDED' AND reason = 'OPERATIONS_PROCEDURE'",
                            id.toString()))
                    .isEqualTo("1");
            assertThat(query(
                            connection,
                            "SELECT after_state FROM identity.audit_record WHERE action = 'DATA_SOURCE_RESUMED'"
                                    + " AND target_id = ?",
                            id.toString()))
                    .startsWith("APPROVED")
                    .contains("提供元の障害が復旧した");
            connection.rollback();
        }
    }

    @Test
    void 停止中でない情報源と知らない情報源は戻さず失敗する() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            UUID approved = source(connection, "APPROVED");

            assertThatThrownBy(() -> execute(connection, script(approved, "確認")))
                    .hasMessageContaining("停止中ではありません");
            connection.rollback();
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            assertThatThrownBy(() -> execute(connection, script(UUID.randomUUID(), "確認")))
                    .hasMessageContaining("情報源がありません");
            connection.rollback();
        }
    }

    @Test
    void 理由が空または長すぎるときは戻さず失敗する() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            UUID id = source(connection, "SUSPENDED");

            assertThatThrownBy(() -> execute(connection, script(id, "   "))).hasMessageContaining("理由");
            connection.rollback();
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            UUID id = source(connection, "SUSPENDED");

            assertThatThrownBy(() -> execute(connection, script(id, "あ".repeat(501))))
                    .hasMessageContaining("理由");
            connection.rollback();
        }
    }
}
