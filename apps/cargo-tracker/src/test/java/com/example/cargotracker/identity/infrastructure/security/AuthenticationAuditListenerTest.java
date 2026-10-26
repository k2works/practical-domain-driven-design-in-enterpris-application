package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.application.internal.commandservices.TotpVerification;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationFailureServiceExceptionEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 認証の監査のリスナーの写し方（Bolt 32、ADR-012 の見直しの 5）。失敗のイベントの型ごとに、ログインの試行のサービスに写すか
 * 監査記録に直接書くかと、その理由を確かめる。誤りだけを数え、ロック中の試行はサービスに、数えない拒否は監査記録に書く。
 */
class AuthenticationAuditListenerTest {

    private final Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
    private final User active = user("active@example.com", UserStatus.ACTIVE);
    private final User suspended = user("suspended@example.com", UserStatus.SUSPENDED);
    private final List<AuditRecord> audit = new ArrayList<>();
    private final List<String> userLookups = new ArrayList<>();
    private final FakeLoginAttemptService attempts = new FakeLoginAttemptService(false);
    private final AuthenticationAuditListener listener = new AuthenticationAuditListener(
            new AuditRecordRepository() {
                @Override
                public void append(AuditRecord auditRecord) {
                    audit.add(auditRecord);
                }

                @Override
                public List<AuditRecord> findByActorUserId(UserId actor) {
                    return List.of();
                }
            },
            new CargoUserDetailsService(users(), companies()),
            companies(),
            Clock.systemUTC(),
            attempts);

    private final CargoUserDetails details = new CargoUserDetails(
            new AuthenticatedActor(active.id(), company.id(), Set.of(Role.SHIPPER), "荷主 太郎", "荷主 A"),
            "active@example.com",
            true,
            null);

    // --- password の段（主体はメールアドレス） ---

    @Test
    void passwordの誤りはサービスに写して数え監査記録には直接書かない() {
        listener.onFailure(new AuthenticationFailureBadCredentialsEvent(
                password("active@example.com"), new BadCredentialsException("誤り")));

        assertThat(attempts.calls).containsExactly("failure:" + AuthenticationRejection.BAD_CREDENTIALS);
        assertThat(audit).isEmpty();
    }

    @Test
    void 存在しない利用者の誤りは数えず操作者なしで記録する() {
        listener.onFailure(new AuthenticationFailureBadCredentialsEvent(
                password("nobody@example.com"), new BadCredentialsException("誤り")));

        assertThat(attempts.calls).isEmpty();
        assertThat(audit).singleElement().satisfies(audited -> {
            assertThat(audited.reason()).isEqualTo(AuthenticationRejection.UNKNOWN_USER.name());
            assertThat(audited.actorUserId()).isNull();
        });
    }

    @Test
    void ロックで拒んだ正しいpasswordはロック中の試行としてサービスに写す() {
        listener.onFailure(
                new AuthenticationFailureLockedEvent(password("active@example.com"), new LockedException("ロック中")));

        assertThat(attempts.calls).containsExactly("locked");
        assertThat(audit).isEmpty();
    }

    @Test
    void 利用停止の利用者は数えず業務の規則の理由で監査記録に直接書く() {
        listener.onFailure(new AuthenticationFailureDisabledEvent(
                password("suspended@example.com"), new DisabledException("利用停止")));

        assertThat(attempts.calls).isEmpty();
        assertThat(audit).singleElement().satisfies(audited -> {
            assertThat(audited.reason()).isEqualTo(AuthenticationRejection.SUSPENDED.name());
            assertThat(audited.actorUserId()).isEqualTo(suspended.id());
        });
    }

    @Test
    void 利用者の読み出しのDBの誤りは利用者を引き直さず数えずに理由SERVICE_ERRORで記録する() {
        listener.onFailure(new AuthenticationFailureServiceExceptionEvent(
                password("active@example.com"), new InternalAuthenticationServiceException("DB の誤り")));

        assertThat(userLookups).as("同じ読み出しを繰り返さない").isEmpty();
        assertThat(attempts.calls).isEmpty();
        assertThat(audit).singleElement().satisfies(audited -> {
            assertThat(audited.action()).isEqualTo(AuditAction.LOGIN_FAILED);
            assertThat(audited.reason()).isEqualTo(AuthenticationRejection.SERVICE_ERROR.name());
            assertThat(audited.actorUserId()).isNull();
        });
    }

    // --- 2 つ目の要素の段（主体は password の段の利用者） ---

    @Test
    void TOTPの拒否は種類の理由でサービスに写して数える() {
        listener.onFailure(new AuthenticationFailureBadCredentialsEvent(
                TotpAuthenticationToken.unauthenticated(details, "123456"),
                new TotpRejectedException(TotpVerification.REJECTED_REUSED)));

        assertThat(attempts.calls).containsExactly("failure:" + AuthenticationRejection.TOTP_REUSED);
        assertThat(userLookups).as("主体の利用者を使い、メールアドレスで引かない").isEmpty();
    }

    @Test
    void 回復コードの拒否は回復コードの理由でサービスに写して数える() {
        listener.onFailure(new AuthenticationFailureBadCredentialsEvent(
                TotpAuthenticationToken.unauthenticated(details, null), new RecoveryCodeRejectedException()));

        assertThat(attempts.calls).containsExactly("failure:" + AuthenticationRejection.INVALID_RECOVERY_CODE);
    }

    @Test
    void 照合の前にロックで拒んだ2つ目の要素はロック中の試行としてサービスに写す() {
        listener.onFailure(new AuthenticationFailureLockedEvent(
                TotpAuthenticationToken.unauthenticated(details, "123456"), new LockedException("ロック中")));

        assertThat(attempts.calls).containsExactly("locked");
        assertThat(audit).isEmpty();
    }

    // --- 成功 ---

    @Test
    void 二つの要素がそろった成功だけがサービスで0に戻す() {
        listener.onSuccess(new AuthenticationSuccessEvent(TotpAuthenticationToken.authenticated(
                details, new SimpleGrantedAuthority(TotpAuthenticationToken.FACTOR_TOTP), SecondFactorPath.TOTP)));

        assertThat(attempts.calls).containsExactly("success");
        assertThat(audit).isEmpty();
    }

    @Test
    void passwordの段の成功は0に戻さずPASSWORD_VERIFIEDを書く() {
        listener.onSuccess(new AuthenticationSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated(details, null, List.of())));

        assertThat(attempts.calls).isEmpty();
        assertThat(audit).singleElement().extracting(AuditRecord::action).isEqualTo(AuditAction.PASSWORD_VERIFIED);
    }

    // --- 補助 ---

    private static Authentication password(String email) {
        return UsernamePasswordAuthenticationToken.unauthenticated(email, "x");
    }

    private User user(String email, UserStatus status) {
        return User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                EmailAddress.of(email),
                "荷主 太郎",
                "{noop}x",
                status,
                Set.of(Role.SHIPPER));
    }

    private UserRepository users() {
        return new UserRepository() {
            @Override
            public void add(User user) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void addRegistered(User user, UserId grantedBy, UtcInstant at) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<User> findByEmail(EmailAddress email) {
                userLookups.add(email.value());
                return List.of(active, suspended).stream()
                        .filter(each -> each.email().equals(email))
                        .findFirst();
            }

            @Override
            public Optional<User> findById(UserId id) {
                return List.of(active, suspended).stream()
                        .filter(each -> each.id().equals(id))
                        .findFirst();
            }

            @Override
            public boolean changePassword(User user) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean changeRoles(User changed, long expectedVersion, UserId grantedBy, UtcInstant at) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean changeStatus(User changed, long expectedVersion, UtcInstant at) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<UserSummary> findSummariesIn(Set<CompanyId> companies) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<UserDetail> findDetail(UserId id) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private CompanyRepository companies() {
        return new CompanyRepository() {
            @Override
            public void add(Company added) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Company> findById(CompanyId id) {
                return company.id().equals(id) ? Optional.of(company) : Optional.empty();
            }

            @Override
            public List<Company> findAllIn(Set<CompanyId> ids) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
