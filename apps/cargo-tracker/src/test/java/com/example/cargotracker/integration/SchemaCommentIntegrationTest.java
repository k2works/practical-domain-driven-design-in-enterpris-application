package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 業務の表と列には日本語のコメントで日本語名を付ける（データモデル「物理名」）。ER 図（SchemaSpy）に日本語名が出る。
 * 追記専用の表は、日本語名の後ろに印（{@code [append-only]}）を付ける。afterMigrate のコールバックがこの印で表を選ぶ。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaCommentIntegrationTest {

    /** 業務のスキーマ。フレームワークの表（platform）は DDL を持たないため対象にしない。 */
    private static final String BUSINESS_SCHEMAS =
            "'quotation', 'routing', 'identity', 'booking', 'notification', 'tracking'";

    private static final Pattern JAPANESE = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]");
    private static final String APPEND_ONLY_MARK = " [append-only]";

    @Autowired
    DataSource dataSource;

    @Test
    void 業務の表には日本語のコメントがある() throws SQLException {
        Map<String, String> comments =
                query("SELECT n.nspname || '.' || c.relname, obj_description(c.oid, 'pg_class') FROM pg_class c"
                        + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE c.relkind = 'r' AND n.nspname IN (" + BUSINESS_SCHEMAS + ")"
                        + " AND c.relname <> 'flyway_schema_history'");

        assertThat(schemasOf(comments)).as("すべての業務のスキーマの表を見ている（空振りしない）").isEqualTo(businessSchemas());
        assertThat(comments)
                .isNotEmpty()
                .allSatisfy((table, comment) ->
                        assertThat(comment).as("%s の表のコメント", table).isNotNull().containsPattern(JAPANESE));
    }

    @Test
    void 業務の表の列には日本語のコメントがある() throws SQLException {
        Map<String, String> comments = query("SELECT c.table_schema || '.' || c.table_name || '.' || c.column_name,"
                + " col_description((c.table_schema || '.' || c.table_name)::regclass, c.ordinal_position)"
                + " FROM information_schema.columns c JOIN information_schema.tables t"
                + " ON t.table_schema = c.table_schema AND t.table_name = c.table_name"
                + " WHERE c.table_schema IN (" + BUSINESS_SCHEMAS + ") AND t.table_type = 'BASE TABLE'"
                + " AND c.table_name <> 'flyway_schema_history'");

        assertThat(schemasOf(comments)).as("すべての業務のスキーマの列を見ている（空振りしない）").isEqualTo(businessSchemas());
        assertThat(comments)
                .isNotEmpty()
                .allSatisfy((column, comment) ->
                        assertThat(comment).as("%s の列のコメント", column).isNotNull().containsPattern(JAPANESE));
    }

    @Test
    void ログインの失敗の回数の注釈は今の数え方に合う() throws SQLException {
        // Bolt 30 の注釈（password と TOTP、成功で 0 に戻す）を Bolt 34c のマイグレーションで直した（IA-INV-03）
        Map<String, String> comments = query("SELECT 'consecutive_failures',"
                + " col_description('identity.login_failure'::regclass, a.attnum) FROM pg_attribute a"
                + " WHERE a.attrelid = 'identity.login_failure'::regclass AND a.attname = 'consecutive_failures'");

        assertThat(comments.get("consecutive_failures"))
                .contains("回復コード")
                .contains("ロック中の試行は数えない")
                .contains("2 つの要素がそろったログインの成功で 0 に戻し");
    }

    @Test
    void 追記専用の表は日本語名の後ろに印を付ける() throws SQLException {
        Map<String, String> comments =
                query("SELECT n.nspname || '.' || c.relname, obj_description(c.oid, 'pg_class') FROM pg_class c"
                        + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE c.relkind = 'r' AND obj_description(c.oid, 'pg_class') LIKE '%append-only%'");

        assertThat(comments.keySet()).isEqualTo(AppendOnlyGrantIntegrationTest.APPEND_ONLY_TABLES);
        assertThat(comments)
                .allSatisfy((table, comment) -> assertThat(comment)
                        .as("%s の表のコメント", table)
                        .endsWith(APPEND_ONLY_MARK)
                        .containsPattern(JAPANESE));
    }

    private Map<String, String> query(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            Map<String, String> rows = new HashMap<>();
            while (result.next()) {
                rows.put(result.getString(1), result.getString(2));
            }
            return rows;
        }
    }

    private static java.util.Set<String> schemasOf(Map<String, String> comments) {
        return comments.keySet().stream()
                .map(name -> name.substring(0, name.indexOf('.')))
                .collect(java.util.stream.Collectors.toSet());
    }

    private static java.util.Set<String> businessSchemas() {
        return java.util.Arrays.stream(BUSINESS_SCHEMAS.split(","))
                .map(name -> name.strip().replace("'", ""))
                .collect(java.util.stream.Collectors.toSet());
    }
}
