package com.example.cargotracker.quotation.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SendBackTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ReviewOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.DocumentFile;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutingRequestedSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SALES)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = TransportRequestReviewController.class,
        properties = {
            "cargotracker.provisional-consignees.companies[0].id=00000000-0000-0000-0000-000000000201",
            "cargotracker.provisional-consignees.companies[0].name=荷受人 A（仮）"
        })
class TransportRequestReviewControllerTest {

    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final UserId STAFF = TestActors.STAFF_USER;
    private static final TransportRequestId ID =
            new TransportRequestId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    /** 受付一覧の待っている時間を決めるための固定の時計（最初の提出から 3 時間 20 分後）。 */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProvisionalConsigneeProperties.class)
    static class Properties {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-05T04:20:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransportRequestReviewService reviewService;

    @MockitoBean
    StaffTransportRequestQueryService queryService;

    @MockitoBean
    StaffQuotationQueryService quotationQueryService;

    private static TransportRequest underReview() {
        return TransportRequest.submit(
                ID,
                NUMBER,
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));
    }

    private static TransportRequestSummary summary() {
        return new TransportRequestSummary(
                NUMBER,
                1,
                TransportRequestStatus.UNDER_REVIEW,
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")),
                new Location("JPTYO"),
                new Location("NLRTM"),
                ShipmentTermsFixture.ARRIVAL_DEADLINE,
                CargoCategory.GENERAL);
    }

    /** 差し戻して版 2 を出し直した、審査中の輸送要求。 */
    private static TransportRequest resubmitted() {
        TransportRequest request = underReview();
        request.sendBack(1, STAFF, "目的地の確認が必要", "目的地の港", new UtcInstant(Instant.parse("2026-10-05T02:00:00Z")));
        request.resubmit(
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T03:00:00Z")));
        return request;
    }

    @Test
    void 受付一覧に審査中の見積依頼を業務番号のリンクと社内の日時と待っている時間と貨物種別で示し内部のIDを出さない() throws Exception {
        given(queryService.findUnderReview()).willReturn(List.of(summary()));

        mockMvc.perform(get("/staff/transport-requests"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/staff/transport-requests/TR-2026-0001\"")))
                .andExpect(content()
                        .string(containsString("2026-10-05 10:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 01:00）")))
                .andExpect(content().string(containsString("JPTYO → NLRTM")))
                .andExpect(content().string(containsString("3 時間 20 分")))
                .andExpect(content().string(containsString("一般")))
                .andExpect(content().string(not(containsString(ID.value().toString()))));
    }

    @Test
    void 受付一覧に経路設計中の見積依頼の見積りを依頼時刻の古い順に示し見積りを開ける() throws Exception {
        given(quotationQueryService.findRoutingRequestedSummaries())
                .willReturn(List.of(
                        new RoutingRequestedSummary(
                                NUMBER,
                                1,
                                QuotationStatus.ROUTING_REQUESTED,
                                new UtcInstant(Instant.parse("2026-10-06T00:30:00Z")),
                                new UtcInstant(Instant.parse("2099-10-09T09:00:00Z")),
                                null),
                        new RoutingRequestedSummary(
                                new TransportRequestNumber(2026, 2),
                                1,
                                QuotationStatus.ROUTING_REQUESTED,
                                new UtcInstant(Instant.parse("2026-10-06T00:40:00Z")),
                                new UtcInstant(Instant.parse("2026-10-05T04:00:00Z")),
                                null)));

        mockMvc.perform(get("/staff/transport-requests"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("経路設計中・荷主承認待ち・予約待ちの見積依頼（依頼時刻の古い順）")))
                .andExpect(content()
                        .string(containsString(
                                "href=\"/staff/transport-requests/TR-2026-0001/quotations/1\">TR-2026-0001 見積 1</a>")))
                .andExpect(content()
                        .string(containsString("2026-10-06 09:30 Asia/Tokyo（UTC+09:00）（UTC 2026-10-06 00:30）")))
                .andExpect(content().string(containsString("2099-10-09 18:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("<td>詳細設計依頼済み</td>")))
                .andExpect(content().string(containsString("<td>失効</td>")));
    }

    @Test
    void 受付一覧の経路設計中の表に荷主承認待ちと荷主承認済みの見積依頼も状態とともに示す() throws Exception {
        UtcInstant requestedAt = new UtcInstant(Instant.parse("2026-10-06T00:30:00Z"));
        UtcInstant expiresAt = new UtcInstant(Instant.parse("2099-10-09T09:00:00Z"));
        given(quotationQueryService.findRoutingRequestedSummaries())
                .willReturn(List.of(
                        new RoutingRequestedSummary(
                                NUMBER,
                                1,
                                QuotationStatus.AWAITING_SHIPPER_APPROVAL,
                                requestedAt,
                                expiresAt,
                                requestedAt),
                        new RoutingRequestedSummary(
                                new TransportRequestNumber(2026, 2),
                                1,
                                QuotationStatus.APPROVED,
                                requestedAt,
                                expiresAt,
                                requestedAt),
                        new RoutingRequestedSummary(
                                new TransportRequestNumber(2026, 3),
                                1,
                                QuotationStatus.APPROVED,
                                requestedAt,
                                new UtcInstant(Instant.parse("2026-10-05T04:00:00Z")),
                                requestedAt)));

        mockMvc.perform(get("/staff/transport-requests"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<td>荷主承認待ち</td>")))
                .andExpect(content().string(containsString("<td>荷主承認済み（予約待ち）</td>")))
                .andExpect(content().string(containsString("<td>失効（荷主承認済み）</td>")));
    }

    @Test
    void 受付一覧に見積提示済みの見積依頼の最新の見積りを有効期限の近い順に示し見積りを開ける() throws Exception {
        given(quotationQueryService.findQuotedSummaries())
                .willReturn(List.of(
                        new QuotedRequestSummary(
                                NUMBER,
                                2,
                                QuotationStatus.PENDING_APPROVAL,
                                new UtcInstant(Instant.parse("2026-10-09T09:00:00Z"))),
                        new QuotedRequestSummary(
                                new TransportRequestNumber(2026, 2),
                                1,
                                QuotationStatus.PRESENTED,
                                new UtcInstant(Instant.parse("2026-10-05T04:00:00Z")))));

        mockMvc.perform(get("/staff/transport-requests"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("見積提示済みの見積依頼（最新の見積りの有効期限の近い順）")))
                .andExpect(content()
                        .string(containsString(
                                "href=\"/staff/transport-requests/TR-2026-0001/quotations/2\">TR-2026-0001 見積 2</a>")))
                .andExpect(content().string(containsString("承認待ち")))
                .andExpect(content().string(containsString("2026-10-09 18:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString(">TR-2026-0002 見積 1</a>")))
                .andExpect(content().string(containsString("<td>失効</td>")));
    }

    @Test
    void 審査中の見積依頼がなければその旨を示す() throws Exception {
        given(queryService.findUnderReview()).willReturn(List.of());

        mockMvc.perform(get("/staff/transport-requests"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("審査中の見積依頼はありません")));
    }

    @Test
    void 審査画面に輸送条件と版と確定と差戻しのフォームを示し対象の版番号を隠し項目で持つ() throws Exception {
        given(queryService.findByNumber(NUMBER)).willReturn(Optional.of(underReview()));

        mockMvc.perform(get("/staff/transport-requests/TR-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TR-2026-0001 版 1")))
                .andExpect(content().string(containsString("荷受人 A（仮）")))
                .andExpect(content().string(containsString("パレット")))
                .andExpect(content().string(containsString("審査を確定する")))
                .andExpect(content().string(containsString("差し戻す")))
                .andExpect(content().string(containsString("name=\"versionNo\" value=\"1\"")));
    }

    @Test
    void 審査画面にこれまでの審査記録を示す() throws Exception {
        given(queryService.findByNumber(NUMBER)).willReturn(Optional.of(resubmitted()));

        mockMvc.perform(get("/staff/transport-requests/TR-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TR-2026-0001 版 2")))
                .andExpect(content().string(containsString("これまでの審査")))
                .andExpect(content().string(containsString("目的地の確認が必要")))
                .andExpect(content().string(containsString("目的地の港")));
    }

    @Test
    void 判断の値が知らない値かないときと版番号がないときは400にする() throws Exception {
        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "DELETE")
                        .param("versionNo", "1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews").param("versionNo", "1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews").param("decision", "APPROVED"))
                .andExpect(status().isBadRequest());
        then(reviewService).shouldHaveNoInteractions();
    }

    @Test
    void 不足事項が長すぎれば不足事項の欄に示す() throws Exception {
        given(reviewService.sendBack(any()))
                .willReturn(new ReviewOutcome.Rejected(ReviewRejection.MISSING_ITEMS_TOO_LONG, 1));
        given(queryService.findByNumber(NUMBER)).willReturn(Optional.of(underReview()));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "SENT_BACK")
                        .param("versionNo", "1")
                        .param("reason", "理由")
                        .param("missingItems", "あ".repeat(4001)))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("reviewForm", "missingItems"))
                .andExpect(content().string(containsString(">不足事項: 4,000 文字までで入力してください</a>")));
    }

    @Test
    void 審査の対象の見積依頼がなければ見つからない() throws Exception {
        given(reviewService.approve(any())).willReturn(new ReviewOutcome.NotFound());

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0099/reviews")
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "確認した"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 確定するとログインした営業担当者で審査し結果を示して見積りの作成へリダイレクトする() throws Exception {
        given(reviewService.approve(new ApproveTransportRequestCommand(NUMBER, 1, STAFF, "確認した")))
                .willReturn(new ReviewOutcome.Reviewed(NUMBER, 1, ReviewDecision.APPROVED));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "確認した"))
                .andExpect(redirectedUrl("/staff/transport-requests/TR-2026-0001/quotations/new"))
                .andExpect(flash().attribute("result", "TR-2026-0001 版 1 の審査を確定しました"));
    }

    @Test
    void 差し戻すと理由と不足事項を渡し結果を一覧に示す() throws Exception {
        given(reviewService.sendBack(new SendBackTransportRequestCommand(NUMBER, 1, STAFF, "契約条件の確認が必要", "取引条件書")))
                .willReturn(new ReviewOutcome.Reviewed(NUMBER, 1, ReviewDecision.SENT_BACK));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "SENT_BACK")
                        .param("versionNo", "1")
                        .param("reason", "契約条件の確認が必要")
                        .param("missingItems", "取引条件書"))
                .andExpect(redirectedUrl("/staff/transport-requests"))
                .andExpect(flash().attribute("result", "TR-2026-0001 版 1 を差し戻しました"));
    }

    @Test
    void 理由のない差戻しはエラー要約と項目の下に示し入力を残す() throws Exception {
        given(reviewService.sendBack(any()))
                .willReturn(new ReviewOutcome.Rejected(ReviewRejection.RATIONALE_REQUIRED, 1));
        given(queryService.findByNumber(NUMBER)).willReturn(Optional.of(underReview()));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "SENT_BACK")
                        .param("versionNo", "1")
                        .param("reason", "")
                        .param("missingItems", "取引条件書"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("reviewForm", "reason"))
                .andExpect(content().string(containsString("入力内容に 1 件の誤りがあります")))
                .andExpect(content().string(containsString(">理由: 差し戻す理由を入力してください</a>")))
                .andExpect(content().string(containsString("取引条件書")));
    }

    @Test
    void 古い版の審査は拒否し版2の内容と隠し項目を示して根拠を空にし再審査を求める() throws Exception {
        given(reviewService.approve(any())).willReturn(new ReviewOutcome.Rejected(ReviewRejection.STALE_VERSION, 2));
        given(queryService.findByNumber(NUMBER)).willReturn(Optional.of(resubmitted()));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "版 1 を確認した"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("審査できませんでした")))
                .andExpect(content().string(containsString("この見積依頼は新しい版 2 が出されています")))
                .andExpect(content().string(containsString("TR-2026-0001 版 2")))
                .andExpect(content().string(containsString("name=\"versionNo\" value=\"2\"")))
                .andExpect(content().string(not(containsString("版 1 を確認した"))));
    }

    @Test
    void ほかの利用者が先に更新していたらその旨を示す() throws Exception {
        given(reviewService.approve(any())).willReturn(new ReviewOutcome.Conflict());
        given(queryService.findByNumber(NUMBER)).willReturn(Optional.of(underReview()));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "確認した"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("他の利用者が先に更新しました")));
    }

    @Test
    void 業務番号がないか形式でなければ見つからない() throws Exception {
        given(queryService.findByNumber(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/staff/transport-requests/TR-2026-0099")).andExpect(status().isNotFound());
        mockMvc.perform(get("/staff/transport-requests/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        then(reviewService).shouldHaveNoInteractions();
    }

    @Test
    void 営業担当者は書類をattachmentとnosniffでダウンロードできる() throws Exception {
        byte[] content = "%PDF-1.7 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        RequiredDocument document = new RequiredDocument(
                1,
                DocumentType.PACKING_LIST,
                "packing.pdf",
                DocumentMediaType.PDF,
                14,
                "a".repeat(64),
                "quotation/x/y");
        given(queryService.findDocument(NUMBER, 1, 1)).willReturn(Optional.of(new DocumentFile(document, content)));

        mockMvc.perform(get("/staff/transport-requests/TR-2026-0001/versions/1/documents/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(content().bytes(content));
    }

    @Test
    void ない書類の取得は見つからない() throws Exception {
        given(queryService.findDocument(NUMBER, 1, 9)).willReturn(Optional.empty());

        mockMvc.perform(get("/staff/transport-requests/TR-2026-0001/versions/1/documents/9"))
                .andExpect(status().isNotFound());
    }
}
