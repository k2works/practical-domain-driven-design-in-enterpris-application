package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditResult;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 認証の監査記録（IA-INV-08、US-18 AC1・AC4）。
 */
class AuditRecordTest {

    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final CompanyId COMPANY = new CompanyId(UUID.randomUUID());
    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-10-06T05:00:00Z"));

    @Test
    void ログインの成功は利用者と企業と時刻を記録する() {
        AuditRecord auditRecord = AuditRecord.loginSucceeded(USER, COMPANY, AT);

        assertThat(auditRecord.action()).isEqualTo(AuditAction.LOGIN_SUCCEEDED);
        assertThat(auditRecord.result()).isEqualTo(AuditResult.SUCCESS);
        assertThat(auditRecord.actorUserId()).isEqualTo(USER);
        assertThat(auditRecord.actorCompanyId()).isEqualTo(COMPANY);
        assertThat(auditRecord.occurredAt()).isEqualTo(AT);
        assertThat(auditRecord.reason()).isNull();
    }

    @Test
    void ログインの失敗は理由を記録する() {
        AuditRecord auditRecord = AuditRecord.loginFailed(USER, COMPANY, AuthenticationRejection.SUSPENDED, AT);

        assertThat(auditRecord.action()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(auditRecord.result()).isEqualTo(AuditResult.FAILURE);
        assertThat(auditRecord.reason()).isEqualTo("SUSPENDED");
        assertThat(auditRecord.actorUserId()).isEqualTo(USER);
    }

    @Test
    void 存在しないメールアドレスでの失敗は操作者を記録しない() {
        AuditRecord auditRecord = AuditRecord.loginFailed(null, null, AuthenticationRejection.UNKNOWN_USER, AT);

        assertThat(auditRecord.actorUserId()).isNull();
        assertThat(auditRecord.actorCompanyId()).isNull();
        assertThat(auditRecord.reason()).isEqualTo("UNKNOWN_USER");
    }

    @Test
    void ログアウトを記録する() {
        AuditRecord auditRecord = AuditRecord.logout(USER, COMPANY, AT);

        assertThat(auditRecord.action()).isEqualTo(AuditAction.LOGOUT);
        assertThat(auditRecord.result()).isEqualTo(AuditResult.SUCCESS);
    }

    @Test
    void 記録ごとに別の識別子を持つ() {
        assertThat(AuditRecord.logout(USER, COMPANY, AT).id())
                .isNotEqualTo(AuditRecord.logout(USER, COMPANY, AT).id());
    }
}
