package com.example.cargotracker.identity.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationTotpEnrollmentMails;
import com.example.cargotracker.identity.domain.events.TotpEnrolled;
import com.example.cargotracker.identity.domain.events.TotpReEnrolled;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.notification.interfaces.api.TotpEnrollmentMailReceipt;
import com.example.cargotracker.notification.interfaces.api.TotpEnrollmentMailRequest;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 認証アプリの登録と再登録のお知らせのイベントハンドラー（Bolt 38d。ADR-017 の決定 4。行 2・3・7・8・9・14）。 */
class TotpEnrollmentMailEventHandlerTest {

    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));
    private static final String ADDRESS = "hanako@example.com";

    private final UserRepository users = mock(UserRepository.class);
    private final List<TotpEnrollmentMailRequest> requests = new ArrayList<>();
    private TotpEnrollmentMailReceipt receipt = new TotpEnrollmentMailReceipt.Sent();
    private final TotpEnrollmentMailEventHandler handler =
            new TotpEnrollmentMailEventHandler(users, new NotificationTotpEnrollmentMails(request -> {
                requests.add(request);
                return receipt;
            }));

    private User user(UserStatus status) {
        User user = User.of(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                EmailAddress.of(ADDRESS),
                "花子",
                "{noop}unused",
                status,
                Set.of(Role.SALES),
                1L);
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void 登録のイベントを受けると本人へ登録のお知らせを監査記録のIDと登録の時刻で送る() {
        // 行 3・14
        User user = user(UserStatus.ACTIVE);
        TotpEnrolled event = new TotpEnrolled(UUID.randomUUID(), user.id(), AT);

        handler.on(event);

        assertThat(requests)
                .containsExactly(new TotpEnrollmentMailRequest(
                        event.auditRecordId(), user.id().value(), ADDRESS, AT, false, "ja"));
    }

    @Test
    void 再登録のイベントを受けると本人へ再登録のお知らせを送る() {
        // 行 2
        User user = user(UserStatus.ACTIVE);
        TotpReEnrolled event = new TotpReEnrolled(UUID.randomUUID(), user.id(), AT);

        handler.on(event);

        assertThat(requests)
                .containsExactly(new TotpEnrollmentMailRequest(
                        event.auditRecordId(), user.id().value(), ADDRESS, AT, true, "ja"));
    }

    @Test
    void 停止中の利用者にも送る() {
        // 行 8（変異 V11。ハンドラーが動く前に止められた場合も）
        User user = user(UserStatus.SUSPENDED);

        handler.on(new TotpEnrolled(UUID.randomUUID(), user.id(), AT));
        handler.on(new TotpReEnrolled(UUID.randomUUID(), user.id(), AT));

        assertThat(requests)
                .hasSize(2)
                .allSatisfy(request -> assertThat(request.email()).isEqualTo(ADDRESS));
    }

    @Test
    void 利用者がいなければ送らずに利用者のIDだけを警告に残し例外にしない() {
        // 行 9
        UserId missing = new UserId(UUID.randomUUID());
        when(users.findById(missing)).thenReturn(Optional.empty());

        try (LogCapture logs = LogCapture.start()) {
            handler.on(new TotpEnrolled(UUID.randomUUID(), missing, AT));
            handler.on(new TotpReEnrolled(UUID.randomUUID(), missing, AT));

            assertThat(logs.text()).contains(missing.value().toString());
        }
        assertThat(requests).isEmpty();
    }

    @Test
    void 送れなくても例外にせず理由を警告に残しメールアドレスは残さない() {
        // 行 7
        User user = user(UserStatus.ACTIVE);
        receipt = new TotpEnrollmentMailReceipt.Failed("MailSendException: 550 <[メールアドレス]>");

        try (LogCapture logs = LogCapture.start()) {
            handler.on(new TotpReEnrolled(UUID.randomUUID(), user.id(), AT));

            assertThat(logs.text())
                    .contains("MailSendException: 550 <[メールアドレス]>")
                    .contains(user.id().value().toString())
                    .doesNotContain(ADDRESS);
        }
    }
}
