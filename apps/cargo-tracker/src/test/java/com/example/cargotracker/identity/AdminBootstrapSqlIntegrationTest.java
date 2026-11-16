package com.example.cargotracker.identity;

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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/**
 * 最初のシステム管理者を作る運用の SQL（{@code ops/sql/admin_bootstrap.sql}。W10 の持ち越し H-1。運用タスク {@code admin:bootstrap}）を、
 * 本物の PostgreSQL に流して確かめる。監督の役割（システム管理者）は画面の登録では付けられない決まりなので、運用の手順で作る
 * （Bolt 38e の決定）。本人の password は、すでにある「password の再設定」で設定する（作った時点では使えないハッシュ）。
 * 共有の DB を壊さないよう、1 つのトランザクションで流して最後にロールバックする（SQL の BEGIN・COMMIT は外して流す）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AdminBootstrapSqlIntegrationTest {

    private static final Path SCRIPT = Path.of("..", "..", "ops", "sql", "admin_bootstrap.sql");

    @Autowired
    DataSource dataSource;

    /** psql の変数 {@code :'name'} を、引用した文字列に置き換える。トランザクションは呼び手が持つので BEGIN・COMMIT を外す。 */
    private static String script(String email, String name, String companyName) throws Exception {
        return Files.readString(SCRIPT, StandardCharsets.UTF_8)
                .replace(":'admin_email'", literal(email))
                .replace(":'admin_name'", literal(name))
                .replace(":'company_name'", literal(companyName))
                .replaceAll("(?m)^BEGIN;\\s*$", "")
                .replaceAll("(?m)^COMMIT;\\s*$", "");
    }

    private static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
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

    private Connection openTransaction() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        // 共有の DB に、ほかのテストが作ったシステム管理者がいても、この中だけ空にして確かめる（最後にロールバックする）
        execute(
                connection,
                "DELETE FROM identity.user_assignment WHERE user_id IN"
                        + " (SELECT user_id FROM identity.user_role WHERE role = 'SYSTEM_ADMIN')");
        execute(connection, "DELETE FROM identity.user_role WHERE role = 'SYSTEM_ADMIN'");
        return connection;
    }

    @Test
    void 最初のシステム管理者を作り_使えないハッシュで_管理範囲は自社_監査記録を残す() throws Exception {
        String email = "first-admin-" + UUID.randomUUID() + "@example.com";
        try (Connection connection = openTransaction()) {
            execute(
                    connection,
                    script("  " + email.toUpperCase() + " ", "初代の管理者", "A 社（テスト " + UUID.randomUUID() + "）"));

            String userId =
                    query(connection, "SELECT CAST(id AS VARCHAR) FROM identity.app_user WHERE email = ?", email);
            assertThat(userId).as("メールアドレスは前後の空白を除き小文字で保存される").isNotNull();
            assertThat(query(connection, "SELECT role FROM identity.user_role WHERE user_id = CAST(? AS UUID)", userId))
                    .isEqualTo("SYSTEM_ADMIN");
            assertThat(query(connection, "SELECT display_name FROM identity.app_user WHERE email = ?", email))
                    .isEqualTo("初代の管理者");
            assertThat(query(connection, "SELECT status FROM identity.app_user WHERE email = ?", email))
                    .isEqualTo("ACTIVE");
            String hash = query(connection, "SELECT password_hash FROM identity.app_user WHERE email = ?", email);
            assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                            .matches("anything", hash))
                    .as("使えないハッシュ。どの password でもログインできない")
                    .isFalse();
            assertThat(query(
                            connection,
                            "SELECT COUNT(*) FROM identity.user_assignment a JOIN identity.app_user u ON u.id = a.user_id"
                                    + " WHERE u.email = ? AND a.scope_kind = 'COMPANY' AND a.scope_id = u.company_id",
                            email))
                    .as("管理範囲は自社")
                    .isEqualTo("1");
            assertThat(query(
                            connection,
                            "SELECT COUNT(*) FROM identity.audit_record WHERE action = 'USER_REGISTERED'"
                                    + " AND target_id = ? AND reason = 'INITIAL_ADMINISTRATOR'",
                            userId))
                    .isEqualTo("1");
            connection.rollback();
        }
    }

    @Test
    void すでにシステム管理者がいるときは作らずに失敗する() throws Exception {
        try (Connection connection = openTransaction()) {
            execute(connection, script("first-" + UUID.randomUUID() + "@example.com", "初代", "A 社（テスト）"));

            assertThatThrownBy(() ->
                            execute(connection, script("second-" + UUID.randomUUID() + "@example.com", "二人目", "A 社")))
                    .hasMessageContaining("すでにシステム管理者がいます");
            connection.rollback();
        }
    }
}
