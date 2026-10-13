package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.commandservices.LoginAttemptService;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationFailureServiceExceptionEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;

/**
 * 認証の成功・失敗とログアウトを、Spring Security のイベントから同じ request の中で同期に監査記録へ書く（IA-INV-08、ADR-012）。
 * 失敗の理由は、利用者があれば業務の規則（IA-INV-09）で求め、なければ存在しないメールアドレスとして操作者を残さない。
 * ロック（失敗の回数）は、この入口からログインの試行のサービスに写して数える（Bolt 32、ADR-012 の見直しの 5）。数えるのは誤り
 * （BadCredentials のイベント）だけで、ロック中・利用停止・入口の DB の誤りは数えない。
 */
public class AuthenticationAuditListener {

    private final AuditRecordRepository auditRecords;
    private final CargoUserDetailsService userDetails;
    private final CompanyRepository companies;
    private final Clock clock;
    private final LoginAttemptService attempts;

    public AuthenticationAuditListener(
            AuditRecordRepository auditRecords,
            CargoUserDetailsService userDetails,
            CompanyRepository companies,
            Clock clock,
            LoginAttemptService attempts) {
        this.auditRecords = auditRecords;
        this.userDetails = userDetails;
        this.companies = companies;
        this.clock = clock;
        this.attempts = attempts;
    }

    /**
     * 成功を記録する。password の段の成功（{@code PASSWORD_VERIFIED}）と、TOTP が通って 2 つの要素がそろった成功
     * （{@code LOGIN_SUCCEEDED}）を分ける（Bolt 31、ADR-012 の見直しの 1）。
     */
    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        boolean secondFactorPassed = event.getAuthentication() instanceof TotpAuthenticationToken;
        actorOf(event.getAuthentication()).ifPresent(actor -> {
            if (secondFactorPassed) {
                // 2 つの要素がそろった成功だけが失敗の回数を 0 に戻す（password の段の成功では戻さない。Bolt 32）
                attempts.recordSuccess(actor.userId(), actor.companyId());
            } else {
                auditRecords.append(AuditRecord.passwordVerified(actor.userId(), actor.companyId(), now()));
            }
        });
    }

    /**
     * 失敗を記録する。TOTP の段の失敗は、主体が password の段で確かめた利用者なので、その利用者と TOTP の理由で書く（Bolt 31、
     * ADR-012 の見直しの 1）。password の段の失敗は、入れたメールアドレスで利用者を引く。
     */
    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        if (event instanceof AuthenticationFailureServiceExceptionEvent
                && !(event.getAuthentication().getPrincipal() instanceof CargoUserDetails)) {
            // 利用者の読み出しが DB の誤りなどで失敗した（枠組みが包んで失敗へ倒す）。同じ読み出しを繰り返さず、利用者を引かずに
            // 記録する。利用者の誤りとして数えない（Bolt 32、確認ポイント 8）
            auditRecords.append(AuditRecord.loginFailed(null, null, AuthenticationRejection.SERVICE_ERROR, now()));
            return;
        }
        Optional<Target> target = targetOf(event);
        if (target.isEmpty()) {
            auditRecords.append(AuditRecord.loginFailed(null, null, AuthenticationRejection.UNKNOWN_USER, now()));
            return;
        }
        Target found = target.get();
        switch (event) {
            // 誤り（password・TOTP・回復コード）だけを数える。ロック中なら、サービスが理由 LOCKED にして数えない
            case AuthenticationFailureBadCredentialsEvent _ ->
                attempts.recordFailure(found.userId(), found.companyId(), found.reason());
            // ロック中の正しい password・ロック中の確かめの前の拒否。数えない
            case AuthenticationFailureLockedEvent _ -> attempts.recordLockedAttempt(found.userId(), found.companyId());
            // 入口の読み出しの DB の誤り（枠組みが包んで失敗へ倒す）。利用者の誤りとして数えない
            case AuthenticationFailureServiceExceptionEvent _ ->
                auditRecords.append(AuditRecord.loginFailed(
                        found.userId(), found.companyId(), AuthenticationRejection.SERVICE_ERROR, now()));
            // 利用停止・無効な企業など（password が正しく、業務の規則で拒んだ）。数えない
            default ->
                auditRecords.append(AuditRecord.loginFailed(found.userId(), found.companyId(), found.reason(), now()));
        }
    }

    /** 失敗の記録の宛先（利用者・企業・理由）。存在しない利用者なら空。 */
    private Optional<Target> targetOf(AbstractAuthenticationFailureEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof CargoUserDetails details) {
            AuthenticatedActor actor = details.actor();
            return Optional.of(new Target(actor.userId(), actor.companyId(), secondFactorReasonOf(event)));
        }
        return userDetails
                .findUser(String.valueOf(event.getAuthentication().getPrincipal()))
                .map(user -> new Target(user.id(), user.companyId(), reasonOf(event, user)));
    }

    private record Target(UserId userId, CompanyId companyId, AuthenticationRejection reason) {}

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
     * 2 つ目の要素（TOTP の段）の失敗の理由。回復コードの拒否は {@code INVALID_RECOVERY_CODE}（Bolt 31c）。TOTP は再利用と未登録
     * （Bolt 31d）を分け、ほかは一致しないとする（利用者には同じ文言。AC2）。
     */
    private static AuthenticationRejection secondFactorReasonOf(AbstractAuthenticationFailureEvent event) {
        if (event.getException() instanceof RecoveryCodeRejectedException) {
            return AuthenticationRejection.INVALID_RECOVERY_CODE;
        }
        if (event.getException() instanceof TotpRejectedException rejected) {
            return switch (rejected.verification()) {
                case REJECTED_REUSED -> AuthenticationRejection.TOTP_REUSED;
                case REJECTED_NOT_ENROLLED -> AuthenticationRejection.TOTP_NOT_ENROLLED;
                case REJECTED_MISMATCH -> AuthenticationRejection.INVALID_TOTP;
                case ACCEPTED -> throw new IllegalStateException("通った結果で拒否された（実装の誤り）");
            };
        }
        return AuthenticationRejection.INVALID_TOTP;
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
