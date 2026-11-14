package com.example.cargotracker.notification.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.notification.application.internal.queryservices.NotificationQueryService;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** C-16 通知一覧（荷主担当者。US-22 AC4。W9 の Bolt 43-3）。送られた通知（メールと同じ内容）と送信の結果を示す。認可は URL の規則（`/customer/**`）が担う。 */
@WithAuthenticatedActor(Role.SHIPPER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = ShipperNotificationController.class)
class ShipperNotificationControllerTest {

    private static final UtcInstant CREATED = new UtcInstant(Instant.parse("2026-11-16T01:00:00Z"));
    private static final UtcInstant SENT = new UtcInstant(Instant.parse("2026-11-16T01:00:05Z"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    NotificationQueryService queryService;

    private static Notification sent(NotificationKind kind, String targetRef) {
        return Notification.pending(
                        UUID.randomUUID(), UUID.randomUUID(), "a@example.com", kind, targetRef, "ja", CREATED)
                .sent(SENT);
    }

    private static Notification failed(NotificationKind kind, String targetRef) {
        return Notification.pending(
                        UUID.randomUUID(), UUID.randomUUID(), "b@example.com", kind, targetRef, "ja", CREATED)
                .failed("SMTP に接続できない");
    }

    @Test
    void 行1_送られた通知の種類と対象と送信の結果と導線を荷主の日時表示で示す() throws Exception {
        given(queryService.list(any()))
                .willReturn(List.of(
                        sent(NotificationKind.QUOTATION_PRESENTED, "TR-2026-0001"),
                        sent(NotificationKind.BOOKING_CONFIRMED, "TR-2026-0002")));

        mockMvc.perform(get("/customer/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>通知</h1>")))
                .andExpect(content().string(containsString("見積りが提示されました")))
                .andExpect(content().string(containsString("TR-2026-0001")))
                .andExpect(content().string(containsString("本予約が確定しました")))
                .andExpect(content().string(containsString("送信済み")))
                .andExpect(content().string(containsString("2026-11-16 10:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("href=\"/customer/transport-requests/TR-2026-0001\"")))
                .andExpect(content().string(containsString("href=\"/customer/bookings\"")));
    }

    @Test
    void 行2_送信に失敗した通知は失敗と宛先を示し技術的な理由は示さない() throws Exception {
        given(queryService.list(any())).willReturn(List.of(failed(NotificationKind.ROUTE_APPROVED, "TR-2026-0003")));

        mockMvc.perform(get("/customer/notifications"))
                .andExpect(content().string(containsString("送信に失敗しました")))
                .andExpect(content().string(containsString("宛先: b@example.com")))
                .andExpect(content().string(not(containsString("SMTP に接続できない"))));
    }

    @Test
    void 行3_通知がなければないと示し表を出さない() throws Exception {
        given(queryService.list(any())).willReturn(List.of());

        mockMvc.perform(get("/customer/notifications"))
                .andExpect(content().string(containsString("通知はまだありません。")))
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    void 行4_追跡が確認中の通知は追跡の照会の結果へ導く() throws Exception {
        given(queryService.list(any()))
                .willReturn(List.of(sent(NotificationKind.TRACKING_UNDER_REVIEW, "CTABCDEFGH2345")));

        mockMvc.perform(get("/customer/notifications"))
                .andExpect(content().string(containsString("貨物の追跡を確認しています")))
                .andExpect(content().string(containsString("href=\"/customer/tracking-records/CTABCDEFGH2345\"")));
    }
}
