package com.example.cargotracker.notification.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 荷主の通知一覧の照会（US-22 AC4。Bolt 43-3）。本人の通知だけを、荷主への業務の通知の種類に絞って読む。 */
class NotificationQueryServiceTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationQueryService service = new NotificationQueryService(notifications);

    private static AuthenticatedActor actor(Role role) {
        return new AuthenticatedActor(
                new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Set.of(role), "担当", "A 社");
    }

    @Test
    void 行1_本人の通知を荷主への業務の通知の種類だけで読む_再設定や招待のメールは含めない() {
        AuthenticatedActor shipper = actor(Role.SHIPPER);
        given(notifications.findRecentByRecipient(
                        eq(shipper.userId().value()), eq(NotificationQueryService.KINDS), anyInt()))
                .willReturn(List.of());

        assertThat(service.list(shipper)).isEmpty();

        then(notifications)
                .should()
                .findRecentByRecipient(
                        shipper.userId().value(), NotificationQueryService.KINDS, NotificationQueryService.LIMIT);
        assertThat(NotificationQueryService.KINDS)
                .containsExactlyInAnyOrderElementsOf(EnumSet.of(
                        NotificationKind.QUOTATION_PRESENTED,
                        NotificationKind.ROUTE_APPROVED,
                        NotificationKind.QUOTATION_EXPIRING,
                        NotificationKind.BOOKING_CONFIRMED,
                        NotificationKind.TRACKING_UNDER_REVIEW));
    }

    @Test
    void 行2_荷主担当者でなければ読まず空を返す() {
        assertThat(service.list(actor(Role.SALES))).isEmpty();

        then(notifications).shouldHaveNoInteractions();
    }

    @Test
    void 行3_読んだ通知はそのまま返す() {
        AuthenticatedActor shipper = actor(Role.SHIPPER);
        Notification notification = Notification.pending(
                UUID.randomUUID(),
                shipper.userId().value(),
                "a@example.com",
                NotificationKind.BOOKING_CONFIRMED,
                "TR-2026-0001",
                "ja",
                new com.example.cargotracker.shared.domain.UtcInstant(java.time.Instant.parse("2026-11-16T01:00:00Z")));
        given(notifications.findRecentByRecipient(
                        eq(shipper.userId().value()), eq(NotificationQueryService.KINDS), anyInt()))
                .willReturn(List.of(notification));

        assertThat(service.list(shipper)).containsExactly(notification);
    }
}
