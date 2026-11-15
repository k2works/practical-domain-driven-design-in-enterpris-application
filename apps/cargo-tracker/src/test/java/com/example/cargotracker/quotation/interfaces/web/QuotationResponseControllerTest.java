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
import com.example.cargotracker.quotation.application.internal.commands.ConsultQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.DeclineQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ConsultOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.DeclineOutcome;
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
    void 回答の画面は経路の確定の案内を見積りの共通の部品の1文だけにして重ねて出さない() throws Exception {
        given(quotationQueryService.findVisible(NUMBER, SHIPPER)).willReturn(List.of(presented()));

        mockMvc.perform(get(RESPONSE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("詳細な経路は、経路設計者の承認後に確定します。")))
                .andExpect(content().string(not(containsString("詳細な経路と日程は経路設計者の承認後に確定します"))));
    }

    @Test
    void 回答の画面に見積りと経路方針と確定の旨と進むボタンと辞退や相談の連絡先を示す() throws Exception {
        given(quotationQueryService.findVisible(NUMBER, SHIPPER)).willReturn(List.of(presented()));

        mockMvc.perform(get(RESPONSE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>見積りへの回答 TR-2026-0001 見積 1</h1>")))
                .andExpect(content().string(containsString("3,730.00 USD")))
                .andExpect(content().string(containsString("2099-10-08 18:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("詳細な経路は、経路設計者の承認後に確定します。")))
                .andExpect(content().string(containsString("この条件で詳細経路設計へ進む</button>")))
                .andExpect(content().string(containsString("条件を相談する</button>")))
                .andExpect(content().string(not(containsString("条件の相談は担当営業にご連絡ください。"))))
                .andExpect(content().string(containsString("理由を付けて辞退する</button>")))
                .andExpect(content().string(containsString("辞退すると、この見積りは使えなくなります")))
                .andExpect(content().string(containsString("辞退すると取り消せないことを確かめました")))
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

    // 辞退（US-24 AC2。W9 の Bolt 46）

    private static final String DECLINE = RESPONSE + "/decline";

    @Test
    void 理由を付けて辞退すると詳細へリダイレクトして結果を示す() throws Exception {
        given(responseService.decline(new DeclineQuotationCommand(NUMBER, 1, SHIPPER, USER, "他社で進めるため")))
                .willReturn(new DeclineOutcome.Declined(NUMBER, 1));

        mockMvc.perform(post(DECLINE).param("reason", "他社で進めるため").param("confirm", "yes"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "TR-2026-0001 見積 1 を辞退しました。理由は担当営業に伝わります"));
    }

    @Test
    void 理由の誤りの辞退は回答の画面に戻して入力した理由を残す() throws Exception {
        given(quotationQueryService.findVisible(NUMBER, SHIPPER)).willReturn(List.of(presented()));
        given(responseService.decline(any())).willReturn(new DeclineOutcome.InvalidReason("辞退の理由を入力してください"));

        mockMvc.perform(post(DECLINE).param("reason", " ").param("confirm", "yes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("辞退の理由を入力してください")))
                .andExpect(content().string(containsString("理由を付けて辞退する</button>")));
    }

    @Test
    void 辞退を受け付けないときと競合したときは詳細に戻して理由を示しない番号は見つからない() throws Exception {
        given(responseService.decline(any()))
                .willReturn(
                        new DeclineOutcome.Rejected(QuotationRejection.EXPIRED),
                        new DeclineOutcome.Rejected(QuotationRejection.ROUTING_REQUESTED),
                        new DeclineOutcome.Conflict(),
                        new DeclineOutcome.NotFound());

        mockMvc.perform(post(DECLINE).param("reason", "理由").param("confirm", "yes"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 は有効期限を過ぎて失効しています。新しい見積りは担当営業にご依頼ください"));
        mockMvc.perform(post(DECLINE).param("reason", "理由").param("confirm", "yes"))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 はすでに詳細経路設計を依頼しているため辞退できません。担当営業にご連絡ください"));
        mockMvc.perform(post(DECLINE).param("reason", "理由").param("confirm", "yes"))
                .andExpect(flash().attribute("problem", "見積りが更新されました。最新の見積りを確かめてから回答してください"));
        mockMvc.perform(post(DECLINE).param("reason", "理由").param("confirm", "yes"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 取り消せないことの確認のチェックがない辞退は受け付けず入力した理由を残して画面を描き直す() throws Exception {
        given(quotationQueryService.findVisible(NUMBER, SHIPPER)).willReturn(List.of(presented()));

        mockMvc.perform(post(DECLINE).param("reason", "他社で進めるため"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("取り消せないことを確かめて、チェックを入れてください")))
                .andExpect(content().string(containsString("他社で進めるため")));
        verify(responseService, never()).decline(any());
    }

    @Test
    void すでに辞退した見積りの二重送信は失効でなく辞退済みとして結果を示す() throws Exception {
        Quotation declined = presented();
        declined.decline(USER, "他社で進めるため", new UtcInstant(Instant.parse("2026-10-06T00:00:00Z")));
        given(quotationQueryService.findVisible(NUMBER, SHIPPER)).willReturn(List.of(declined));
        given(responseService.decline(any())).willReturn(new DeclineOutcome.Rejected(QuotationRejection.EXPIRED));

        mockMvc.perform(post(DECLINE).param("reason", "理由").param("confirm", "yes"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "TR-2026-0001 見積 1 はすでに辞退しています"));
    }

    @Test
    void 業務番号の形でない番号の辞退は受け付けない() throws Exception {
        mockMvc.perform(post("/customer/transport-requests/XX/quotations/1/response/decline")
                        .param("reason", "理由"))
                .andExpect(status().isNotFound());
        verify(responseService, never()).decline(any());
    }

    // 相談（US-24 AC3。W9 の Bolt 46-1）

    private static final String CONSULT = RESPONSE + "/consult";

    @Test
    void 相談すると詳細へリダイレクトして受け付けたことと問い合わせでの確かめ方を示す() throws Exception {
        given(responseService.consult(new ConsultQuotationCommand(NUMBER, 1, SHIPPER, USER, "納期を早められるか")))
                .willReturn(new ConsultOutcome.Requested(NUMBER, 1));

        mockMvc.perform(post(CONSULT).param("content", "納期を早められるか"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(
                        flash().attribute("result", "TR-2026-0001 見積 1 の条件の相談を受け付けました。担当者が確認します。進捗は「問い合わせ」で確かめられます"));
    }

    @Test
    void 内容の誤りの相談は回答の画面に戻して入力した内容を残す() throws Exception {
        given(quotationQueryService.findVisible(NUMBER, SHIPPER)).willReturn(List.of(presented()));
        given(responseService.consult(any())).willReturn(new ConsultOutcome.InvalidContent("相談内容を入力してください"));

        mockMvc.perform(post(CONSULT).param("content", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("相談内容を入力してください")))
                .andExpect(content().string(containsString("条件を相談する</button>")));
    }

    @Test
    void 相談を受け付けないときは詳細に戻して理由を示しない番号は見つからない() throws Exception {
        given(responseService.consult(any()))
                .willReturn(
                        new ConsultOutcome.Rejected(QuotationRejection.EXPIRED),
                        new ConsultOutcome.Rejected(QuotationRejection.ROUTING_REQUESTED),
                        new ConsultOutcome.NotFound());

        mockMvc.perform(post(CONSULT).param("content", "相談"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 は有効期限を過ぎて失効しています。新しい見積りは担当営業にご依頼ください"));
        mockMvc.perform(post(CONSULT).param("content", "相談"))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 はすでに詳細経路設計を依頼しているため相談できません。担当営業にご連絡ください"));
        mockMvc.perform(post(CONSULT).param("content", "相談")).andExpect(status().isNotFound());
    }

    @Test
    void 業務番号の形でない番号の相談は受け付けない() throws Exception {
        mockMvc.perform(post("/customer/transport-requests/XX/quotations/1/response/consult")
                        .param("content", "相談"))
                .andExpect(status().isNotFound());
        verify(responseService, never()).consult(any());
    }

    @Test
    void 業務番号の形でない番号への回答は依頼しない() throws Exception {
        mockMvc.perform(post("/customer/transport-requests/XX/quotations/1/response"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("依頼しました"))));
        verify(responseService, never()).requestRouteDesign(any());
    }
}
