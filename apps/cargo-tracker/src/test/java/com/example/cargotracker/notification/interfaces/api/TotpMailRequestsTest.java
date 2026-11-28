package com.example.cargotracker.notification.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.notification.application.internal.commands.SendTotpEnrollmentMailCommand;
import com.example.cargotracker.notification.application.internal.commands.SendTotpResetMailCommand;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TOTP のお知らせの要求とコマンドは、ログや例外の文にメールアドレスを出さない（行 11。Bolt 38d）。 */
class TotpMailRequestsTest {

    private static final String ADDRESS = "hanako@example.com";
    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));
    private static final UUID AUDIT_RECORD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void 解除のお知らせの要求はメールアドレスを文字列に出さない() {
        String text = new TotpResetMailRequest(AUDIT_RECORD_ID, USER_ID, ADDRESS, AT, "ja").toString();

        assertThat(text)
                .doesNotContain(ADDRESS)
                .contains(AUDIT_RECORD_ID.toString())
                .contains(USER_ID.toString());
    }

    @Test
    void 解除のお知らせのコマンドはIDだけを文字列に出す() {
        String text = new SendTotpResetMailCommand(AUDIT_RECORD_ID, USER_ID, ADDRESS, AT, "ja").toString();

        assertThat(text)
                .isEqualTo("SendTotpResetMailCommand[auditRecordId=" + AUDIT_RECORD_ID + ", recipientUserId=" + USER_ID
                        + "]");
    }

    @Test
    void 登録と再登録のお知らせの要求はメールアドレスを文字列に出さない() {
        String text = new TotpEnrollmentMailRequest(AUDIT_RECORD_ID, USER_ID, ADDRESS, AT, true, "ja").toString();

        assertThat(text)
                .doesNotContain(ADDRESS)
                .contains(AUDIT_RECORD_ID.toString())
                .contains(USER_ID.toString());
    }

    @Test
    void 登録と再登録のお知らせのコマンドはIDだけを文字列に出す() {
        String text = new SendTotpEnrollmentMailCommand(AUDIT_RECORD_ID, USER_ID, ADDRESS, AT, false, "ja").toString();

        assertThat(text)
                .isEqualTo("SendTotpEnrollmentMailCommand[auditRecordId=" + AUDIT_RECORD_ID + ", recipientUserId="
                        + USER_ID + ", reEnrolled=false]");
    }
}
