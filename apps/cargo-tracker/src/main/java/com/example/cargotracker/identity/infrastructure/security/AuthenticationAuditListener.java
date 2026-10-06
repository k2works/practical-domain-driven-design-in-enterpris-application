package com.example.cargotracker.identity.infrastructure.security;

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
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;

/**
 * 認証の成功・失敗とログアウトを、Spring Security のイベントから同じ request の中で同期に監査記録へ書く（IA-INV-08、ADR-012）。
 * 失敗の理由は、利用者があれば業務の規則（IA-INV-09）で求め、なければ存在しないメールアドレスとして操作者を残さない。
 * W5 のロック（失敗の回数）も、ここで数える（ADR-011 の決定 2）。
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

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        actorOf(event.getAuthentication())
                .ifPresent(actor ->
                        auditRecords.append(AuditRecord.loginSucceeded(actor.userId(), actor.companyId(), now())));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Optional<User> user =
                userDetails.findUser(String.valueOf(event.getAuthentication().getPrincipal()));
        if (user.isEmpty()) {
            auditRecords.append(AuditRecord.loginFailed(null, null, AuthenticationRejection.UNKNOWN_USER, now()));
            return;
        }
        User found = user.get();
        AuthenticationRejection reason = companies
                .findById(found.companyId())
                .flatMap(found::authenticationRejection)
                .filter(rejection ->
                        event.getException() instanceof org.springframework.security.authentication.DisabledException)
                .orElse(AuthenticationRejection.BAD_CREDENTIALS);
        auditRecords.append(AuditRecord.loginFailed(found.id(), found.companyId(), reason, now()));
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
