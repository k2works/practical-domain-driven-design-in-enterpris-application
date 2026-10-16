package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.example.cargotracker.identity.application.internal.commandservices.EnrollmentResult;
import com.example.cargotracker.identity.application.internal.commandservices.EnrollmentStart;
import com.example.cargotracker.identity.application.internal.commandservices.ReEnrollmentStart;
import com.example.cargotracker.identity.application.internal.commandservices.RecoveryCodeVerification;
import com.example.cargotracker.identity.application.internal.commandservices.RecoveryCodeVerificationService;
import com.example.cargotracker.identity.application.internal.commandservices.TotpEnrollmentService;
import com.example.cargotracker.identity.application.internal.commandservices.TotpReEnrollmentService;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.EnrollingUser;
import com.example.cargotracker.identity.domain.model.valueobjects.SessionId;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 認証アプリの再登録で監査を書けなければ、資格の置き換えと回復コードの発行し直しも戻ることを確かめる（Bolt 33b の開発レビュー。監査の
 * 書き込みを spy で失敗させる。単体テストのメモリ上のリポジトリは戻らないので、PostgreSQL で確かめる）。
 */
class TotpReEnrollmentRollbackIntegrationTest extends IdentitySpyIntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    @Autowired
    TotpReEnrollmentService reEnrollment;

    @Autowired
    TotpEnrollmentService enrollment;

    @Autowired
    RecoveryCodeVerificationService recovery;

    @Autowired
    TotpCredentialRepository credentials;

    @Autowired
    RecoveryCodeRepository recoveryCodes;

    @Autowired
    Clock clock;

    private Company company;

    @BeforeEach
    void 時計を置き企業を登録する() {
        ((MutableClock) clock).setInstant(NOW);
        company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(company);
    }

    @Test
    void 監査を書けなければ資格も回復コードも元のまま() {
        UserId user = new UserId(UUID.randomUUID());
        users.add(User.of(
                user,
                company.id(),
                EmailAddress.of("rollback-" + UUID.randomUUID() + "@example.com"),
                "荷主 太郎",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SHIPPER)));
        EnrollingUser actor = new EnrollingUser(user, company.id(), EmailAddress.of("user@example.com"));
        EncryptedSecret oldSecret = ((EnrollmentStart.Started) enrollment.begin(actor, Optional.empty())).secret();
        List<String> oldCodes = ((EnrollmentResult.Enrolled)
                        enrollment.complete(actor, oldSecret, codeAt(user, oldSecret, -1)))
                .recoveryCodes();
        EncryptedSecret renewed = ((ReEnrollmentStart.Started) reEnrollment.begin(user, Optional.empty())).secret();
        doThrow(new IllegalStateException("監査の表に届かない"))
                .when(auditRecords)
                .append(argThat(written -> written.action() == AuditAction.TOTP_RE_ENROLLED));
        UtcInstant startedAt = new UtcInstant(clock.instant());
        String code = codeAt(user, renewed, 0);

        assertThatThrownBy(() -> reEnrollment.complete(user, renewed, code, startedAt, new SessionId("session")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(credentials.findByUserId(user).orElseThrow().secret())
                .as("秘密は置き換えていない")
                .isEqualTo(oldSecret);
        assertThat(recoveryCodes.findUnusedByUserId(user)).hasSize(10);
        assertThat(recovery.verify(user, company.id(), oldCodes.getFirst()))
                .as("前の回復コードが使える")
                .isEqualTo(RecoveryCodeVerification.ACCEPTED);
    }

    private String codeAt(UserId user, EncryptedSecret secret, long offset) {
        return totpCodes.code(user, secret, TotpCredential.stepAt(new UtcInstant(clock.instant())) + offset);
    }
}
