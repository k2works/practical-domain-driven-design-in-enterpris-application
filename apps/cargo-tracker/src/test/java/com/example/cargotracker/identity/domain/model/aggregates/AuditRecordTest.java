package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditResult;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
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
    void passwordの段の成功はログインの成功と分けて記録する() {
        AuditRecord auditRecord = AuditRecord.passwordVerified(USER, COMPANY, AT);

        assertThat(auditRecord.action()).isEqualTo(AuditAction.PASSWORD_VERIFIED);
        assertThat(auditRecord.result()).isEqualTo(AuditResult.SUCCESS);
        assertThat(auditRecord.actorUserId()).isEqualTo(USER);
        assertThat(auditRecord.actorCompanyId()).isEqualTo(COMPANY);
        assertThat(auditRecord.reason()).isNull();
    }

    @Test
    void TOTPの登録は利用者と企業を記録する() {
        AuditRecord auditRecord = AuditRecord.totpEnrolled(USER, COMPANY, AT);

        assertThat(auditRecord.action()).isEqualTo(AuditAction.TOTP_ENROLLED);
        assertThat(auditRecord.result()).isEqualTo(AuditResult.SUCCESS);
        assertThat(auditRecord.actorUserId()).isEqualTo(USER);
        assertThat(auditRecord.actorCompanyId()).isEqualTo(COMPANY);
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

    @Test
    void sessionの取消しは失敗として理由とsessionの主体を記録する() {
        AuditRecord auditRecord = AuditRecord.sessionRevoked(USER, COMPANY, AuthenticationRejection.SUSPENDED, AT);

        assertThat(auditRecord.action()).isEqualTo(AuditAction.SESSION_REVOKED);
        assertThat(auditRecord.result()).isEqualTo(AuditResult.FAILURE);
        assertThat(auditRecord.reason()).isEqualTo("SUSPENDED");
        assertThat(auditRecord.actorUserId()).isEqualTo(USER);
        assertThat(auditRecord.actorCompanyId()).isEqualTo(COMPANY);
        assertThat(auditRecord.occurredAt()).isEqualTo(AT);
    }

    @Test
    void sessionの取消しは理由と主体を欠かせない() {
        assertThatThrownBy(() -> AuditRecord.sessionRevoked(USER, COMPANY, null, AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditRecord.sessionRevoked(null, COMPANY, AuthenticationRejection.UNKNOWN_USER, AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditRecord.sessionRevoked(USER, null, AuthenticationRejection.UNKNOWN_USER, AT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void 範囲外の試行は失敗として理由と対象の利用者を記録し版と変更前後を持たない() {
        UserId target = new UserId(UUID.randomUUID());

        AuditRecord auditRecord = AuditRecord.adminScopeDenied(USER, COMPANY, target, AT);

        assertThat(auditRecord.action()).isEqualTo(AuditAction.ADMIN_SCOPE_DENIED);
        assertThat(auditRecord.result()).isEqualTo(AuditResult.FAILURE);
        assertThat(auditRecord.reason()).isEqualTo("OUT_OF_SCOPE");
        assertThat(auditRecord.target())
                .isEqualTo(new AuditTarget("USER", target.value().toString(), null, null, null));
    }

    @Test
    void 範囲外の試行は主体と対象を欠かせない() {
        UserId target = new UserId(UUID.randomUUID());

        assertThatThrownBy(() -> AuditRecord.adminScopeDenied(null, COMPANY, target, AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditRecord.adminScopeDenied(USER, null, target, AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditRecord.adminScopeDenied(USER, COMPANY, null, AT))
                .isInstanceOf(NullPointerException.class);
    }
}
