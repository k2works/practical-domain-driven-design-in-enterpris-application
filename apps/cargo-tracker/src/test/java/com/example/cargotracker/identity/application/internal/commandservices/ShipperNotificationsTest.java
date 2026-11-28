package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationShipperMails;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.ShipperNotificationKind;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 荷主への業務の通知の宛先の解決と送信（US-22。Bolt 43。台帳 D-166。仮）。その企業の利用中の荷主担当者全員へ送る。停止中の利用者・荷主担当者でない
 * 利用者には送らない。送れなかった宛先があっても、残りには送る。
 */
class ShipperNotificationsTest {

    private static final CompanyId COMPANY = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000043a0001"));
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-0000043a0002");
    private static final String BASE = "https://tracker.example.com";

    private final UserRepository users = mock(UserRepository.class);
    private final NotificationShipperMails mails = mock(NotificationShipperMails.class);
    private final ShipperNotifications notifications = new ShipperNotifications(users, mails, BASE);

    private static UserSummary summary(String email, UserStatus status, Role role) {
        return new UserSummary(
                new UserId(UUID.randomUUID()),
                "A 社",
                "担当",
                new EmailAddress(email),
                status,
                Set.of(role),
                false,
                List.of(),
                0);
    }

    @Test
    void 行1_その企業の利用中の荷主担当者全員へ画面への導線つきで送る() {
        UserSummary first = summary("a@example.com", UserStatus.ACTIVE, Role.SHIPPER);
        UserSummary second = summary("b@example.com", UserStatus.ACTIVE, Role.SHIPPER);
        given(users.findSummariesIn(Set.of(COMPANY))).willReturn(List.of(first, second));
        given(mails.send(any(), any(), any(), any(), any(), any())).willReturn(Optional.empty());

        int failures = notifications.notifyCompany(
                COMPANY,
                ShipperNotificationKind.QUOTATION_PRESENTED,
                EVENT,
                "TR-2026-0001",
                "/customer/transport-requests");

        assertThat(failures).isZero();
        then(mails)
                .should()
                .send(
                        EVENT,
                        first.id(),
                        first.email(),
                        ShipperNotificationKind.QUOTATION_PRESENTED,
                        "TR-2026-0001",
                        BASE + "/customer/transport-requests");
        then(mails).should().send(eq(EVENT), eq(second.id()), eq(second.email()), any(), eq("TR-2026-0001"), any());
    }

    @Test
    void 行2_停止中の利用者と荷主担当者でない利用者には送らない() {
        given(users.findSummariesIn(Set.of(COMPANY)))
                .willReturn(List.of(
                        summary("suspended@example.com", UserStatus.SUSPENDED, Role.SHIPPER),
                        summary("sales@example.com", UserStatus.ACTIVE, Role.SALES)));

        int failures = notifications.notifyCompany(
                COMPANY, ShipperNotificationKind.BOOKING_CONFIRMED, EVENT, "TR-2026-0001", "/customer/bookings");

        assertThat(failures).isZero();
        then(mails).should(never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 行3_送れなかった宛先があっても残りへ送り失敗の数を返す() {
        UserSummary first = summary("a@example.com", UserStatus.ACTIVE, Role.SHIPPER);
        UserSummary second = summary("b@example.com", UserStatus.ACTIVE, Role.SHIPPER);
        given(users.findSummariesIn(Set.of(COMPANY))).willReturn(List.of(first, second));
        given(mails.send(eq(EVENT), eq(first.id()), any(), any(), any(), any())).willReturn(Optional.of("接続できない"));
        given(mails.send(eq(EVENT), eq(second.id()), any(), any(), any(), any()))
                .willReturn(Optional.empty());

        int failures = notifications.notifyCompany(
                COMPANY, ShipperNotificationKind.ROUTE_APPROVED, EVENT, "TR-2026-0001", "/customer/transport-requests");

        assertThat(failures).isEqualTo(1);
        then(mails).should().send(eq(EVENT), eq(second.id()), any(), any(), any(), any());
    }
}
