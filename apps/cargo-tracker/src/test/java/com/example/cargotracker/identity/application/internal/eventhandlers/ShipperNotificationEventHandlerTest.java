package com.example.cargotracker.identity.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.identity.application.internal.commandservices.ShipperNotifications;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.ShipperNotificationKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.events.TrackingPlacedUnderReview;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 業務のイベントを受けて荷主へ通知する（US-22 AC1。Bolt 43-1）。契機ごとに宛先の企業・種類・対象・画面への導線を確かめる。荷主企業を持たないイベント
 * （通知を使わない以前の発行）では送らない。
 */
class ShipperNotificationEventHandlerTest {

    private static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000043b0001");
    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-11-16T01:00:00Z"));

    private final ShipperNotifications notifications = mock(ShipperNotifications.class);
    private final ShipperNotificationEventHandler handler = new ShipperNotificationEventHandler(notifications);

    private static QuotationPresented presented(UUID company) {
        return new QuotationPresented(
                UUID.randomUUID(), 1, UUID.randomUUID(), 1, AT, List.of("SGSIN"), AT, AT, AT, company, "TR-2026-0001");
    }

    @Test
    void 行1_見積りの提示を受けて見積依頼の詳細への導線つきで通知する() {
        handler.on(presented(COMPANY));

        then(notifications)
                .should()
                .notifyCompany(
                        eq(new CompanyId(COMPANY)),
                        eq(ShipperNotificationKind.QUOTATION_PRESENTED),
                        any(),
                        eq("TR-2026-0001"),
                        eq("/customer/transport-requests/TR-2026-0001"));
    }

    @Test
    void 行2_経路の割当てを受けて荷主の承認が必要と通知する() {
        QuotationRouteAssigned event = new QuotationRouteAssigned(
                UUID.randomUUID(), 1, UUID.randomUUID(), 1, "RC-2026-0001", 1, AT, COMPANY, "TR-2026-0001");

        handler.on(event);

        then(notifications)
                .should()
                .notifyCompany(
                        eq(new CompanyId(COMPANY)),
                        eq(ShipperNotificationKind.ROUTE_APPROVED),
                        any(),
                        eq("TR-2026-0001"),
                        eq("/customer/transport-requests/TR-2026-0001"));
    }

    @Test
    void 行3_本予約の確定を受けて予約一覧への導線つきで通知する() {
        BookingConfirmed event = new BookingConfirmed(
                UUID.randomUUID(),
                1,
                "CTABCDEFGH2345",
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "TR-2026-0001",
                "RC-2026-0001",
                1,
                AT,
                1L,
                new CompanyId(COMPANY),
                new CompanyId(UUID.randomUUID()));

        handler.on(event);

        then(notifications)
                .should()
                .notifyCompany(
                        eq(new CompanyId(COMPANY)),
                        eq(ShipperNotificationKind.BOOKING_CONFIRMED),
                        any(),
                        eq("TR-2026-0001"),
                        eq("/customer/bookings"));
    }

    @Test
    void 行4_追跡が確認中になったら追跡の照会の結果への導線つきで通知する() {
        TrackingPlacedUnderReview event = new TrackingPlacedUnderReview(
                "CTABCDEFGH2345", UUID.randomUUID(), "OUT_OF_SEQUENCE", 3, AT, 5L, COMPANY);

        handler.on(event);

        then(notifications)
                .should()
                .notifyCompany(
                        eq(new CompanyId(COMPANY)),
                        eq(ShipperNotificationKind.TRACKING_UNDER_REVIEW),
                        any(),
                        eq("CTABCDEFGH2345"),
                        eq("/customer/tracking-records/CTABCDEFGH2345"));
    }

    @Test
    void 行5_荷主企業を持たないイベントでは送らない() {
        handler.on(presented(null));

        then(notifications).shouldHaveNoInteractions();
    }

    @Test
    void 行6_契機の_ID_は種類と事象から決まり_同じ事象なら同じで_種類が違えば違う() {
        UUID quotationId = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        QuotationPresented first =
                new QuotationPresented(quotationId, 1, request, 1, AT, List.of(), AT, AT, AT, COMPANY, "TR-2026-0001");
        QuotationRouteAssigned assigned =
                new QuotationRouteAssigned(quotationId, 1, request, 1, "RC-2026-0001", 1, AT, COMPANY, "TR-2026-0001");

        handler.on(first);
        handler.on(first);
        handler.on(assigned);

        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        then(notifications)
                .should(org.mockito.Mockito.times(3))
                .notifyCompany(any(), any(), ids.capture(), any(), any());
        assertThat(ids.getAllValues().get(0)).isEqualTo(ids.getAllValues().get(1));
        assertThat(ids.getAllValues().get(2)).isNotEqualTo(ids.getAllValues().get(0));
    }
}
