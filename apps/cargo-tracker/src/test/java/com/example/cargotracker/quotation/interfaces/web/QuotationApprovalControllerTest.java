package com.example.cargotracker.quotation.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.quotation.application.internal.commands.ApproveQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.ShipperApprovalOutcome;
import com.example.cargotracker.quotation.application.internal.queryservices.QuotationQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** C-17 見積りと経路の承認（US-24 AC4・AC5。Bolt 20）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SHIPPER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = QuotationApprovalController.class)
class QuotationApprovalControllerTest {

    private static final CompanyId SHIPPER = TestActors.SHIPPER_COMPANY;
    private static final UserId USER = TestActors.SHIPPER_USER;
    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final String DETAIL = "/customer/transport-requests/TR-2026-0001";
    private static final String APPROVAL = DETAIL + "/quotations/1/approval";

    /** 見積りの失効の表示を決める固定の時計（見本の有効期限 2099-10-08T09:00:00Z より前）。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-08T06:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QuotationResponseService responseService;

    @MockitoBean
    QuotationQueryService quotationQueryService;

    private static Quotation routingRequested() {
        UtcInstant at = new UtcInstant(Instant.parse("2026-10-05T04:00:00Z"));
        Quotation quotation =
                Quotation.create(new QuotationId(UUID.randomUUID()), new TransportRequestId(UUID.randomUUID()), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at);
        quotation.presentInternally(new UserId(UUID.randomUUID()), at);
        quotation.requestRouteDesign(USER, new UtcInstant(Instant.parse("2026-10-06T00:30:00Z")));
        quotation.clearDomainEvents();
        return quotation;
    }

    private static Quotation awaitingApproval() {
        Quotation quotation = routingRequested();
        quotation.assignRoute(QuotationFixture.assignedRoute(), new UtcInstant(Instant.parse("2026-10-07T05:01:00Z")));
        quotation.clearDomainEvents();
        return quotation;
    }

    @Test
    void 承認の画面に見積りと確定した経路と承認の意味とボタンを示す() throws Exception {
        given(quotationQueryService.findVisible(NUMBER, SHIPPER)).willReturn(List.of(awaitingApproval()));

        mockMvc.perform(get(APPROVAL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>見積りと経路の承認 TR-2026-0001 見積 1</h1>")))
                .andExpect(content().string(containsString("3,730.00 USD")))
                .andExpect(content().string(containsString("2099-10-08 18:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("RC-2026-0001 経路版 1")))
                .andExpect(content().string(containsString("2026-10-07 14:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("V-201")))
                .andExpect(content().string(containsString("JPTYO → SGSIN")))
                .andExpect(content().string(containsString("SGSIN → NLRTM")))
                .andExpect(content().string(containsString("2099-10-31 09:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("この見積りと経路で承認しますか")))
                .andExpect(content().string(containsString("有効期限（2099-10-08 18:00 Asia/Tokyo（UTC+09:00））までに承認してください。")))
                .andExpect(content().string(containsString("承認は本予約の確定ではありません。承認の後、担当営業が本予約を確定します。")))
                .andExpect(content().string(containsString("この見積りと経路で承認する</button>")))
                .andExpect(content().string(not(containsString("<!-- C-17"))));
    }

    @Test
    void 承認できない見積りの画面は詳細に戻して理由を示し荷主に見えない番号は見つからない() throws Exception {
        Quotation approved = awaitingApproval();
        approved.approveByShipper(USER, new UtcInstant(Instant.parse("2026-10-08T05:00:00Z")));
        given(quotationQueryService.findVisible(NUMBER, SHIPPER))
                .willReturn(List.of(routingRequested()), List.of(approved));

        mockMvc.perform(get(APPROVAL))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 はまだ承認できる見積りではありません。経路が確定したら承認できます"));
        mockMvc.perform(get(APPROVAL))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "TR-2026-0001 見積 1 の見積りと経路はすでに承認しています"));
        mockMvc.perform(get(DETAIL + "/quotations/2/approval")).andExpect(status().isNotFound());
        mockMvc.perform(get("/customer/transport-requests/XX/quotations/1/approval"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 承認するとログインした荷主担当者で承認し詳細へリダイレクトして結果を示す() throws Exception {
        given(responseService.approve(new ApproveQuotationCommand(NUMBER, 1, SHIPPER, USER)))
                .willReturn(new ShipperApprovalOutcome.Approved(NUMBER, 1));

        mockMvc.perform(post(APPROVAL))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "TR-2026-0001 見積 1 の見積りと経路を承認しました。担当営業が本予約を確定します。"));
    }

    @Test
    void 承認できないときと競合したときは詳細に戻して理由を示しない番号は見つからない() throws Exception {
        given(responseService.approve(any()))
                .willReturn(
                        new ShipperApprovalOutcome.Rejected(QuotationRejection.EXPIRED),
                        new ShipperApprovalOutcome.Rejected(QuotationRejection.REPLACED),
                        new ShipperApprovalOutcome.Rejected(QuotationRejection.ALREADY_APPROVED),
                        new ShipperApprovalOutcome.Rejected(QuotationRejection.NOT_AWAITING_SHIPPER_APPROVAL),
                        new ShipperApprovalOutcome.Conflict(),
                        new ShipperApprovalOutcome.NotFound());

        mockMvc.perform(post(APPROVAL))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 は有効期限を過ぎたため承認できません。新しい見積りは担当営業にご依頼ください"));
        mockMvc.perform(post(APPROVAL))
                .andExpect(flash().attribute(
                                "problem", "TR-2026-0001 見積 1 は新しい見積りに置き換えられたため承認できません。見積依頼の詳細で最新の状況をご確認ください"));
        mockMvc.perform(post(APPROVAL)).andExpect(flash().attribute("result", "TR-2026-0001 見積 1 の見積りと経路はすでに承認しています"));
        mockMvc.perform(post(APPROVAL))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 はまだ承認できる見積りではありません。経路が確定したら承認できます"));
        mockMvc.perform(post(APPROVAL)).andExpect(flash().attribute("problem", "見積りが更新されました。最新の見積りと経路を確かめてから承認してください"));
        mockMvc.perform(post(APPROVAL)).andExpect(status().isNotFound());
    }

    @Test
    void 業務番号の形でない番号への承認は承認しない() throws Exception {
        mockMvc.perform(post("/customer/transport-requests/XX/quotations/1/approval"))
                .andExpect(status().isNotFound());
        verify(responseService, never()).approve(any());
    }
}
