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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SendBackTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ReviewOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TransportRequestReviewController.class,
        properties = {
            "cargotracker.provisional-actor.shipper-company-id=00000000-0000-0000-0000-000000000001",
            "cargotracker.provisional-actor.user-id=00000000-0000-0000-0000-000000000101",
            "cargotracker.provisional-actor.staff-user-id=00000000-0000-0000-0000-000000000301",
            "cargotracker.provisional-consignees.companies[0].id=00000000-0000-0000-0000-000000000201",
            "cargotracker.provisional-consignees.companies[0].name=荷受人 A（仮）"
        })
class TransportRequestReviewControllerTest {

    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final UserId STAFF = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301"));
    private static final TransportRequestId ID =
            new TransportRequestId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ProvisionalActorProperties.class, ProvisionalConsigneeProperties.class})
    static class Properties {}

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransportRequestReviewService reviewService;

    @MockitoBean
    TransportRequestQueryService queryService;

    private static TransportRequest underReview() {
        return TransportRequest.submit(
                ID,
                NUMBER,
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));
    }

    @Test
    void 受付一覧に審査中の見積依頼を業務番号のリンクと社内の日時で示し内部のIDを出さない() throws Exception {
        given(queryService.findUnderReview()).willReturn(List.of(underReview()));

        mockMvc.perform(get("/staff/transport-requests"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/staff/transport-requests/TR-2026-0001\"")))
                .andExpect(content()
                        .string(containsString("2026-10-05 10:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 01:00）")))
                .andExpect(content().string(containsString("JPTYO → NLRTM")))
                .andExpect(content().string(not(containsString(ID.value().toString()))));
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
        given(queryService.findByNumberForStaff(NUMBER)).willReturn(Optional.of(underReview()));

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
    void 確定すると仮の営業担当者で審査し結果を一覧に示すためにリダイレクトする() throws Exception {
        given(reviewService.approve(new ApproveTransportRequestCommand(NUMBER, 1, STAFF, "確認した")))
                .willReturn(new ReviewOutcome.Reviewed(NUMBER, 1, ReviewDecision.APPROVED));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "確認した"))
                .andExpect(redirectedUrl("/staff/transport-requests"))
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
        given(queryService.findByNumberForStaff(NUMBER)).willReturn(Optional.of(underReview()));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "SENT_BACK")
                        .param("versionNo", "1")
                        .param("reason", "")
                        .param("missingItems", "取引条件書"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("reviewForm", "reason"))
                .andExpect(content().string(containsString("入力内容に 1 件の誤りがあります")))
                .andExpect(content().string(containsString("差し戻す理由を入力してください")))
                .andExpect(content().string(containsString("取引条件書")));
    }

    @Test
    void 古い版の審査は拒否し最新版を示して再審査を求める() throws Exception {
        given(reviewService.approve(any())).willReturn(new ReviewOutcome.Rejected(ReviewRejection.STALE_VERSION, 2));
        given(queryService.findByNumberForStaff(NUMBER)).willReturn(Optional.of(underReview()));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "確認した"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("この見積依頼は新しい版 2 が出されています")));
    }

    @Test
    void ほかの利用者が先に更新していたらその旨を示す() throws Exception {
        given(reviewService.approve(any())).willReturn(new ReviewOutcome.Conflict());
        given(queryService.findByNumberForStaff(NUMBER)).willReturn(Optional.of(underReview()));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/reviews")
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "確認した"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("他の利用者が先に更新しました")));
    }

    @Test
    void 業務番号がないか形式でなければ見つからない() throws Exception {
        given(queryService.findByNumberForStaff(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/staff/transport-requests/TR-2026-0099")).andExpect(status().isNotFound());
        mockMvc.perform(get("/staff/transport-requests/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        then(reviewService).shouldHaveNoInteractions();
    }
}
