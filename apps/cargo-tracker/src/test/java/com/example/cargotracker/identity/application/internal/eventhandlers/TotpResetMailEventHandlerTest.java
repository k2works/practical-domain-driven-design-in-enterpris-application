package com.example.cargotracker.identity.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationTotpResetMails;
import com.example.cargotracker.identity.domain.events.TotpResetByAdmin;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.notification.interfaces.api.TotpResetMailReceipt;
import com.example.cargotracker.notification.interfaces.api.TotpResetMailRequest;
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

/** 管理者による TOTP の登録の解除のお知らせのイベントハンドラー（Bolt 38d。ADR-017 の決定 4。行 1・7・8・9・14）。 */
class TotpResetMailEventHandlerTest {

    private static final UtcInstant RESET_AT = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));
    private static final String ADDRESS = "hanako@example.com";

    private final UserRepository users = mock(UserRepository.class);
    private final List<TotpResetMailRequest> requests = new ArrayList<>();
    private TotpResetMailReceipt receipt = new TotpResetMailReceipt.Sent();
    private final TotpResetMailEventHandler handler =
            new TotpResetMailEventHandler(users, new NotificationTotpResetMails(request -> {
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
    void 解除のイベントを受けると本人の登録メールアドレスへ監査記録のIDと解除の時刻で送る() {
        // 行 1・14（日時はイベントの時刻）
        User user = user(UserStatus.ACTIVE);
        TotpResetByAdmin event = new TotpResetByAdmin(UUID.randomUUID(), user.id(), RESET_AT);

        handler.on(event);

        assertThat(requests)
                .containsExactly(new TotpResetMailRequest(
                        event.auditRecordId(), user.id().value(), ADDRESS, RESET_AT, "ja"));
    }

    @Test
    void 停止中の利用者にも送る() {
        // 行 8（変異 V11。リンクのない知らせで、招待の前例の理由（使えないリンク）が当たらない）
        User user = user(UserStatus.SUSPENDED);

        handler.on(new TotpResetByAdmin(UUID.randomUUID(), user.id(), RESET_AT));

        assertThat(requests)
                .singleElement()
                .satisfies(request -> assertThat(request.email()).isEqualTo(ADDRESS));
    }

    @Test
    void 利用者がいなければ送らずに利用者のIDだけを警告に残し例外にしない() {
        // 行 9
        UserId missing = new UserId(UUID.randomUUID());
        when(users.findById(missing)).thenReturn(Optional.empty());

        try (LogCapture logs = LogCapture.start()) {
            handler.on(new TotpResetByAdmin(UUID.randomUUID(), missing, RESET_AT));

            assertThat(logs.text()).contains(missing.value().toString());
        }
        assertThat(requests).isEmpty();
    }

    @Test
    void 送れなくても例外にせず理由を警告に残しメールアドレスは残さない() {
        // 行 7
        User user = user(UserStatus.ACTIVE);
        receipt = new TotpResetMailReceipt.Failed("MailSendException: 550 <[メールアドレス]>");

        try (LogCapture logs = LogCapture.start()) {
            handler.on(new TotpResetByAdmin(UUID.randomUUID(), user.id(), RESET_AT));

            assertThat(logs.text())
                    .contains("MailSendException: 550 <[メールアドレス]>")
                    .contains(user.id().value().toString())
                    .doesNotContain(ADDRESS);
        }
    }
}
