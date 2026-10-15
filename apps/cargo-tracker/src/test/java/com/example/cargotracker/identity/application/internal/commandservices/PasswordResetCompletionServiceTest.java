package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.rules.PasswordPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** password の再設定のサービスの、確定の後の session の失効（Bolt 33a、SEC-08）。 */
class PasswordResetCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T01:00:00Z");

    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final UserSessionRevoker sessions = mock(UserSessionRevoker.class);
    private final PasswordResetCompletionService service = new PasswordResetCompletionService(
            tokens,
            token -> "a".repeat(64),
            users,
            companies,
            password -> "{bcrypt}hash",
            password -> false,
            sessions,
            mock(AuditRecordRepository.class),
            new PasswordPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new ImmediateTransactions());

    private final Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
    private final User user = User.of(
            new UserId(UUID.randomUUID()),
            company.id(),
            EmailAddress.of("shipper@example.com"),
            "荷主 太郎",
            "{bcrypt}old",
            UserStatus.ACTIVE,
            Set.of(Role.SHIPPER));
    private final PasswordResetToken token = PasswordResetToken.issue(
            UUID.randomUUID(), user.id(), "a".repeat(64), new UtcInstant(NOW.minusSeconds(60)));

    private void givenUsableToken() {
        when(tokens.findById(token.id())).thenReturn(Optional.of(token));
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(companies.findById(company.id())).thenReturn(Optional.of(company));
        when(tokens.markUsed(any(), any())).thenReturn(true);
        when(users.changePassword(any())).thenReturn(true);
    }

    @Test
    void sessionを失効させられなくてもpasswordは変わっているので完了とする() {
        givenUsableToken();
        when(sessions.revokeAll(any())).thenThrow(new IllegalStateException("session の表に届かない"));

        assertThat(service.complete(token.id(), "a brand new long passphrase"))
                .isEqualTo(new PasswordResetCompletion.Completed());
        verify(users).changePassword(any());
    }

    @Test
    void 無効なリンクではsessionを失効させない() {
        givenUsableToken();
        when(tokens.markUsed(any(), any())).thenReturn(false);

        assertThat(service.complete(token.id(), "a brand new long passphrase"))
                .isEqualTo(new PasswordResetCompletion.Invalid());
        verify(sessions, never()).revokeAll(any());
        verify(users, never()).changePassword(any());
    }

    /** トランザクションを開かずにそのまま呼ぶ（単体テスト用）。 */
    private static final class ImmediateTransactions extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
