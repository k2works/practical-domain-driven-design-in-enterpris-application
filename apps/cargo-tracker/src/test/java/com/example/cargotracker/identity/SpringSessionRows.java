package com.example.cargotracker.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * テストから Spring Session JDBC の表（{@code platform.spring_session}・{@code spring_session_attributes}）を見る・変える補助
 * （Bolt 32d）。cookie の値（Base64 の session ID）の読み方と表の名前をこの 1 か所に置く。Spring Session の版を上げて表や cookie の
 * 形が変わったら、ここだけを直す。
 */
public final class SpringSessionRows {

    private static final String ATTRIBUTES_OF_SESSION = " FROM platform.spring_session_attributes a"
            + " JOIN platform.spring_session s ON s.primary_id = a.session_primary_id WHERE s.session_id = ?";

    private final JdbcTemplate jdbc;

    public SpringSessionRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** cookie の値（Base64）から session ID を取る。 */
    public static String sessionId(Cookie session) {
        return new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.US_ASCII);
    }

    /** session の行があるか（捨てられていないか）。 */
    public boolean exists(Cookie session) {
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM platform.spring_session WHERE session_id = ?", Integer.class, sessionId(session));
        return rows != null && rows > 0;
    }

    /** session の属性の名前。 */
    public List<String> attributeNames(Cookie session) {
        return jdbc.queryForList("SELECT a.attribute_name" + ATTRIBUTES_OF_SESSION, String.class, sessionId(session));
    }

    /** session の属性の直列化したバイト列。 */
    public List<byte[]> attributeBytes(Cookie session) {
        return jdbc.query(
                "SELECT a.attribute_bytes" + ATTRIBUTES_OF_SESSION, (rs, n) -> rs.getBytes(1), sessionId(session));
    }

    /** 最終アクセスの時刻を、いまから指定の時間だけ前にする（無操作の時間を作る。Spring Session は実時間で期限を見る）。 */
    public void idle(Cookie session, Duration duration) {
        long lastAccess = Instant.now().minus(duration).toEpochMilli();
        int updated = jdbc.update(
                "UPDATE platform.spring_session SET last_access_time = ?,"
                        + " expiry_time = ? + max_inactive_interval * 1000 WHERE session_id = ?",
                lastAccess,
                lastAccess,
                sessionId(session));
        assertThat(updated).as("session の行").isEqualTo(1);
    }

    /** その利用者の session の無操作の期限（秒）。 */
    public Integer maxInactiveIntervalOf(String principalName) {
        return jdbc.queryForObject(
                "SELECT max_inactive_interval FROM platform.spring_session WHERE principal_name = ?",
                Integer.class,
                principalName);
    }
}
