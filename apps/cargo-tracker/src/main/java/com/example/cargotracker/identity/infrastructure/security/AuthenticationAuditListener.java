package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.commandservices.TotpVerification;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;

/**
 * 認証の成功・失敗とログアウトを、Spring Security のイベントから同じ request の中で同期に監査記録へ書く（IA-INV-08、ADR-012）。
 * 失敗の理由は、利用者があれば業務の規則（IA-INV-09）で求め、なければ存在しないメールアドレスとして操作者を残さない。
 * W5 のロック（失敗の回数）は Bolt 32 で、この記録を入口にして identity のコマンドサービスで数える（ADR-012 の見直しの 5）。
 */
public class AuthenticationAuditListener {

    private final AuditRecordRepository auditRecords;
    private final CargoUserDetailsService userDetails;
    private final CompanyRepository companies;
    private final Clock clock;

    public AuthenticationAuditListener(
            AuditRecordRepository auditRecords,
            CargoUserDetailsService userDetails,
            CompanyRepository companies,
            Clock clock) {
        this.auditRecords = auditRecords;
        this.userDetails = userDetails;
        this.companies = companies;
        this.clock = clock;
    }

    /**
     * 成功を記録する。password の段の成功（{@code PASSWORD_VERIFIED}）と、TOTP が通って 2 つの要素がそろった成功
     * （{@code LOGIN_SUCCEEDED}）を分ける（Bolt 31、ADR-012 の見直しの 1）。
     */
    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        boolean totpPassed = event.getAuthentication() instanceof TotpAuthenticationToken;
        actorOf(event.getAuthentication())
                .ifPresent(actor -> auditRecords.append(
                        totpPassed
                                ? AuditRecord.loginSucceeded(actor.userId(), actor.companyId(), now())
                                : AuditRecord.passwordVerified(actor.userId(), actor.companyId(), now())));
    }

    /**
     * 失敗を記録する。TOTP の段の失敗は、主体が password の段で確かめた利用者なので、その利用者と TOTP の理由で書く（Bolt 31、
     * ADR-012 の見直しの 1）。password の段の失敗は、入れたメールアドレスで利用者を引く。
     */
    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof CargoUserDetails details) {
            AuthenticatedActor actor = details.actor();
            auditRecords.append(AuditRecord.loginFailed(actor.userId(), actor.companyId(), totpReasonOf(event), now()));
            return;
        }
        Optional<User> user =
                userDetails.findUser(String.valueOf(event.getAuthentication().getPrincipal()));
        if (user.isEmpty()) {
            auditRecords.append(AuditRecord.loginFailed(null, null, AuthenticationRejection.UNKNOWN_USER, now()));
            return;
        }
        User found = user.get();
        auditRecords.append(AuditRecord.loginFailed(found.id(), found.companyId(), reasonOf(event, found), now()));
    }

    /**
     * 失敗の理由。password が正しく、利用停止・無効な企業で拒否したとき（DisabledException）だけ業務の規則で理由を求め、
     * ほかは password の誤りとする（password を先に照合するため、利用停止でも password が誤っていれば password の誤り）。
     */
    private AuthenticationRejection reasonOf(AbstractAuthenticationFailureEvent event, User user) {
        if (!(event.getException() instanceof DisabledException)) {
            return AuthenticationRejection.BAD_CREDENTIALS;
        }
        return companies
                .findById(user.companyId())
                .flatMap(user::authenticationRejection)
                .orElse(AuthenticationRejection.BAD_CREDENTIALS);
    }

    /**
     * TOTP の段の失敗の理由。回復コードの拒否は {@code INVALID_RECOVERY_CODE}（Bolt 31c）。TOTP は再利用だけを分け、ほかは一致しない
     * とする（利用者には同じ文言。AC2）。
     */
    private static AuthenticationRejection totpReasonOf(AbstractAuthenticationFailureEvent event) {
        if (event.getException() instanceof RecoveryCodeRejectedException) {
            return AuthenticationRejection.INVALID_RECOVERY_CODE;
        }
        return event.getException() instanceof TotpRejectedException rejected
                        && rejected.verification() == TotpVerification.REJECTED_REUSED
                ? AuthenticationRejection.TOTP_REUSED
                : AuthenticationRejection.INVALID_TOTP;
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        actorOf(event.getAuthentication())
                .ifPresent(actor -> auditRecords.append(AuditRecord.logout(actor.userId(), actor.companyId(), now())));
    }

    private static Optional<AuthenticatedActor> actorOf(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CargoUserDetails details
                ? Optional.of(details.actor())
                : Optional.empty();
    }

    private UtcInstant now() {
        return new UtcInstant(clock.instant());
    }
}
