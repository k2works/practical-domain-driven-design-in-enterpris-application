package com.example.cargotracker.identity.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationUserInvitationMails;
import com.example.cargotracker.identity.domain.events.UserRegistered;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenHasher;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.notification.interfaces.api.UserInvitationMailReceipt;
import com.example.cargotracker.notification.interfaces.api.UserInvitationMailRequest;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 利用者の招待のメールのイベントハンドラー（Bolt 38b2c。ADR-017 の決定 4）。送れなかったときの扱い。 */
class UserInvitationMailEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-26T05:00:00Z");

    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final PasswordResetTokenGenerator generator = mock(PasswordResetTokenGenerator.class);
    private final PasswordResetTokenHasher hasher = mock(PasswordResetTokenHasher.class);
    private final List<UserInvitationMailRequest> requests = new ArrayList<>();
    private final UserInvitationMailEventHandler handler = new UserInvitationMailEventHandler(
            tokens,
            users,
            companies,
            generator,
            hasher,
            new NotificationUserInvitationMails(request -> {
                requests.add(request);
                return new UserInvitationMailReceipt.Failed("MailSendException: 550 <[メールアドレス]>: [リンク]");
            }),
            "https://cargo.example/password-reset/",
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 送れなくても例外にせず登録とトークンのIDで警告を残しトークンは残す() {
        // 行 6（T-206。警告の全文で確かめる）
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        EmailAddress email = EmailAddress.of("hanako@example.com");
        User user = User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                email,
                "新人 花子",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SALES));
        when(tokens.lockForIssue(user.id())).thenReturn(true);
        when(tokens.existsByRequestId(any())).thenReturn(false);
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(companies.findById(company.id())).thenReturn(Optional.of(company));
        when(generator.generate()).thenReturn("plain-invitation-token");
        when(hasher.hash("plain-invitation-token")).thenReturn("a".repeat(64));
        UserRegistered event = new UserRegistered(UUID.randomUUID(), user.id(), new UtcInstant(NOW));

        String logs;
        try (LogCapture capture = LogCapture.start()) {
            handler.on(event);
            logs = capture.text();
        }

        ArgumentCaptor<PasswordResetToken> issued = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).add(issued.capture());
        assertThat(requests)
                .singleElement()
                .satisfies(request -> assertThat(request.invitationUrl())
                        .isEqualTo("https://cargo.example/password-reset/plain-invitation-token"));
        assertThat(logs)
                .contains("利用者の招待のメールを送れなかった（本人が A-05 で設定する）: 登録 " + event.registrationId() + "、トークン "
                        + issued.getValue().id() + "、理由 MailSendException: 550 <[メールアドレス]>: [リンク]")
                .doesNotContain("plain-invitation-token")
                .doesNotContain(email.value());
    }
}
