package com.example.cargotracker.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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
        return sessionId(session.getValue());
    }

    /** cookie の値（Base64 の文字列）から session ID を取る（画面の層のテストはブラウザの cookie の値を渡す。Bolt 32b）。 */
    public static String sessionId(String cookieValue) {
        return new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.US_ASCII);
    }

    /**
     * session の属性の値を書き換える（Spring Session JDBC の既定の JDK の直列化で書く。Bolt 32b）。画面の層のテストでは
     * サーバーの時計を進められないので、ログインの時刻を前にずらして 8 時間の上限の近い session を作る。
     */
    public void putAttribute(String cookieValue, String name, Serializable value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        } catch (IOException e) {
            throw new IllegalStateException("session の属性を書けない: " + name, e);
        }
        int updated = jdbc.update(
                "UPDATE platform.spring_session_attributes SET attribute_bytes = ? WHERE attribute_name = ?"
                        + " AND session_primary_id = (SELECT primary_id FROM platform.spring_session WHERE session_id = ?)",
                bytes.toByteArray(),
                name,
                sessionId(cookieValue));
        assertThat(updated).as("session の属性 " + name).isEqualTo(1);
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

    /** session の属性の値（Spring Session JDBC の既定の JDK の直列化を戻す）。属性がなければ null。 */
    public Object attribute(Cookie session, String name) {
        List<byte[]> values = jdbc.query(
                "SELECT a.attribute_bytes" + ATTRIBUTES_OF_SESSION + " AND a.attribute_name = ?",
                (rs, n) -> rs.getBytes(1),
                sessionId(session),
                name);
        if (values.isEmpty()) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(values.getFirst()))) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("session の属性を読めない: " + name, e);
        }
    }

    /** 最終アクセスの時刻を、いまから指定の時間だけ前にする（無操作の時間を作る。Spring Session は実時間で期限を見る）。 */
    public void idle(Cookie session, Duration duration) {
        idle(session.getValue(), duration);
    }

    /** {@link #idle(Cookie, Duration)} の、ブラウザの cookie の値（Base64 の文字列）を受ける形（画面の層のテスト。Bolt 32b）。 */
    public void idle(String cookieValue, Duration duration) {
        long lastAccess = Instant.now().minus(duration).toEpochMilli();
        int updated = jdbc.update(
                "UPDATE platform.spring_session SET last_access_time = ?,"
                        + " expiry_time = ? + max_inactive_interval * 1000 WHERE session_id = ?",
                lastAccess,
                lastAccess,
                sessionId(cookieValue));
        assertThat(updated).as("session の行").isEqualTo(1);
    }

    /** 最終アクセスの時刻（Spring Session が実時間で書く。延長で新しくなることを確かめる。Bolt 32b）。 */
    public Instant lastAccessTime(Cookie session) {
        Long millis = jdbc.queryForObject(
                "SELECT last_access_time FROM platform.spring_session WHERE session_id = ?",
                Long.class,
                sessionId(session));
        assertThat(millis).as("session の行").isNotNull();
        return Instant.ofEpochMilli(millis);
    }

    /** その利用者の session の無操作の期限（秒）。 */
    public Integer maxInactiveIntervalOf(String principalName) {
        return jdbc.queryForObject(
                "SELECT max_inactive_interval FROM platform.spring_session WHERE principal_name = ?",
                Integer.class,
                principalName);
    }
}
