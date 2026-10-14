package com.example.cargotracker.identity;

import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * テストから監査記録の表（{@code identity.audit_record}）を読む補助（Bolt 32d の開発レビュー）。共有の表なので、いつもその
 * テストの利用者で絞る（T-113）。
 */
public final class AuditRecordRows {

    private final JdbcTemplate jdbc;

    public AuditRecordRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** その利用者の監査記録の操作（順は問わない）。 */
    public List<String> actionsOf(UserId userId) {
        return jdbc.queryForList(
                "SELECT action FROM identity.audit_record WHERE actor_user_id = ?", String.class, userId.value());
    }

    /** その利用者の、その時刻の監査記録の操作（監査の順を時刻で確かめる。同じ時刻の行は順を問わない）。 */
    public List<String> actionsAt(UserId userId, Instant at) {
        return jdbc.queryForList(
                "SELECT action FROM identity.audit_record WHERE actor_user_id = ? AND occurred_at = ?",
                String.class,
                userId.value(),
                OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
    }
}
