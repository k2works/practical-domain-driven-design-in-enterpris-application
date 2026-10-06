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
import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteDesignRequestOutcome;
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

/** C-05 見積りへの回答（US-24 AC1。Bolt 12）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SHIPPER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = QuotationResponseController.class)
class QuotationResponseControllerTest {

    private static final CompanyId SHIPPER = TestActors.SHIPPER_COMPANY;
    private static final UserId USER = TestActors.SHIPPER_USER;
    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final String DETAIL = "/customer/transport-requests/TR-2026-0001";
    private static final String RESPONSE = DETAIL + "/quotations/1/response";

    /** 見積りの失効の表示を決める固定の時計（見本の有効期限 2099-10-08T09:00:00Z より前）。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-06T01:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QuotationResponseService responseService;

    @MockitoBean
    QuotationQueryService quotationQueryService;

    private static Quotation presented() {
        UtcInstant at = new UtcInstant(Instant.parse("2026-10-05T04:00:00Z"));
        Quotation quotation =
                Quotation.create(new QuotationId(UUID.randomUUID()), new TransportRequestId(UUID.randomUUID()), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at);
        quotation.presentInternally(new UserId(UUID.randomUUID()), at);
        quotation.clearDomainEvents();
        return quotation;
    }

    @Test
    void 回答の画面に見積りと経路方針と確定の旨と進むボタンと辞退や相談の連絡先を示す() throws Exception {
        given(quotationQueryService.findVisible(NUMBER, SHIPPER)).willReturn(List.of(presented()));

        mockMvc.perform(get(RESPONSE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>見積りへの回答 TR-2026-0001 見積 1</h1>")))
                .andExpect(content().string(containsString("3,730.00 USD")))
                .andExpect(content().string(containsString("2099-10-08 18:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("詳細な経路と日程は経路設計者の承認後に確定します。")))
                .andExpect(content().string(containsString("この条件で詳細経路設計へ進む</button>")))
                .andExpect(content().string(containsString("辞退や条件の相談は担当営業にご連絡ください。")))
                .andExpect(content().string(containsString("この回答は予約の確定ではありません。経路が決まったら、あらためて見積りと経路を確かめて承認していただきます。")))
                .andExpect(content().string(containsString("回答を取り消したい場合は、業務番号を添えて担当営業にご連絡ください。")))
                .andExpect(content().string(not(containsString("<!-- C-05"))));
    }

    @Test
    void 回答できない見積りの画面は詳細に戻して理由を示し荷主に見えない番号は見つからない() throws Exception {
        Quotation responded = presented();
        responded.requestRouteDesign(USER, new UtcInstant(Instant.parse("2026-10-06T00:00:00Z")));
        given(quotationQueryService.findVisible(NUMBER, SHIPPER)).willReturn(List.of(responded));

        mockMvc.perform(get(RESPONSE))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "TR-2026-0001 見積 1 ですでに詳細経路設計を依頼しています"));
        mockMvc.perform(get(DETAIL + "/quotations/2/response")).andExpect(status().isNotFound());
        mockMvc.perform(get("/customer/transport-requests/XX/quotations/1/response"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 進むとログインした荷主担当者で依頼し詳細へリダイレクトして結果を示す() throws Exception {
        given(responseService.requestRouteDesign(new RequestRouteDesignCommand(NUMBER, 1, SHIPPER, USER)))
                .willReturn(new RouteDesignRequestOutcome.Requested(NUMBER, 1));

        mockMvc.perform(post(RESPONSE))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "TR-2026-0001 見積 1 で詳細経路設計を依頼しました"));
    }

    @Test
    void 依頼できないときと競合したときは詳細に戻して理由を示しない番号は見つからない() throws Exception {
        given(responseService.requestRouteDesign(any()))
                .willReturn(
                        new RouteDesignRequestOutcome.Rejected(QuotationRejection.EXPIRED),
                        new RouteDesignRequestOutcome.Rejected(QuotationRejection.REPLACED),
                        new RouteDesignRequestOutcome.Rejected(QuotationRejection.ROUTING_REQUESTED),
                        new RouteDesignRequestOutcome.Conflict(),
                        new RouteDesignRequestOutcome.NotFound());

        mockMvc.perform(post(RESPONSE))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 は有効期限を過ぎて失効しています。新しい見積りは担当営業にご依頼ください"));
        mockMvc.perform(post(RESPONSE))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 は新しい見積りに置き換えられました。見積依頼の詳細で最新の状況をご確認ください"));
        mockMvc.perform(post(RESPONSE)).andExpect(flash().attribute("result", "TR-2026-0001 見積 1 ですでに詳細経路設計を依頼しています"));
        mockMvc.perform(post(RESPONSE)).andExpect(flash().attribute("problem", "見積りが更新されました。最新の見積りを確かめてから回答してください"));
        mockMvc.perform(post(RESPONSE)).andExpect(status().isNotFound());
    }

    @Test
    void 業務番号の形でない番号への回答は依頼しない() throws Exception {
        mockMvc.perform(post("/customer/transport-requests/XX/quotations/1/response"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("依頼しました"))));
        verify(responseService, never()).requestRouteDesign(any());
    }
}
