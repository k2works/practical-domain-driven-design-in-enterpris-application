package com.example.cargotracker.tracking.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerTrackingQueryService;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerTrackingView;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** C-10 追跡の照会（荷主担当者。US-09 AC1、BR-07。Bolt 27）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SHIPPER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = CustomerTrackingController.class)
class CustomerTrackingControllerTest {

    private static final String LIST = "/customer/tracking-records";
    private static final String DETAIL = LIST + "/" + TrackingFixture.TRACKING_NUMBER.value();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerTrackingQueryService queryService;

    private static TrackingRecordSummary summary(String trackingNumber, TrackingStatus status) {
        return new TrackingRecordSummary(
                new TrackingNumber(trackingNumber),
                status,
                TrackingFixture.at("2026-11-15T00:00:00Z"),
                TrackingFixture.at("2026-11-16T03:00:00Z"),
                TrackingFixture.at("2026-10-26T01:00:00Z"));
    }

    private static CustomerTrackingView view(List<CustomerMilestone> milestones) {
        return new CustomerTrackingView(
                TrackingFixture.TRACKING_NUMBER,
                TrackingStatus.PICKED_UP,
                TrackingFixture.at("2026-11-15T00:00:00Z"),
                TrackingFixture.at("2026-11-16T03:00:00Z"),
                TrackingFixture.schedule().legs(),
                milestones);
    }

    private static CustomerMilestone pickup() {
        return new CustomerMilestone(
                MilestoneKind.PICKUP,
                new Location("JPTYO"),
                TrackingFixture.at("2026-11-01T02:30:00Z"),
                new Source(SourceKind.FIELD_RECORD, "F-118", TrackingFixture.at("2026-11-01T03:00:00Z")));
    }

    // 一覧

    @Test
    void 一覧はログインした荷主担当者の企業の追跡記録を追跡番号と現在状態と最新の到着見込みで示す() throws Exception {
        given(queryService.recent(TestActors.SHIPPER_COMPANY))
                .willReturn(new RecentTrackingRecords(
                        List.of(summary("CTABCDEFGH2345", TrackingStatus.PICKED_UP)), false, 50));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>追跡の照会</h1>")))
                // 照会の入力と一覧を見出しで分ける。並びは荷主の語（本予約の確定）で示す
                .andExpect(content().string(containsString("<h2 class=\"h4\" id=\"search-heading\">追跡番号で照会する</h2>")))
                .andExpect(content().string(containsString("role=\"search\" aria-labelledby=\"search-heading\"")))
                .andExpect(
                        content().string(containsString("<h2 class=\"h4\" id=\"tracking-records-heading\">自社の貨物</h2>")))
                .andExpect(content().string(containsString("追跡中の貨物（本予約の確定の新しい順）")))
                .andExpect(content().string(containsString("<th scope=\"col\">現在の状態</th>")))
                .andExpect(content()
                        .string(containsString(
                                "href=\"/customer/tracking-records/CTABCDEFGH2345\">CTABCDEFGH2345</a>")))
                .andExpect(content().string(containsString("<td>集荷済み</td>")))
                // 荷主の日時表示（UTC を併記しない）。最新の到着見込みを示す
                .andExpect(content().string(containsString("<td>2026-11-16 12:00 Asia/Tokyo（UTC+09:00）</td>")))
                .andExpect(content().string(not(containsString("（UTC 2026"))));
    }

    @Test
    void 自社の追跡記録がなければ本予約の確定を待つ案内を示し表を出さない() throws Exception {
        given(queryService.recent(TestActors.SHIPPER_COMPANY))
                .willReturn(new RecentTrackingRecords(List.of(), false, 50));

        mockMvc.perform(get(LIST))
                .andExpect(content().string(containsString("追跡中の貨物はまだありません。担当営業が本予約を確定すると、ここに出ます。")))
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    void 上限を超えたら新しい件数だけを示していると示す() throws Exception {
        given(queryService.recent(TestActors.SHIPPER_COMPANY))
                .willReturn(new RecentTrackingRecords(
                        Collections.nCopies(50, summary("CTABCDEFGH2345", TrackingStatus.PICKUP_SCHEDULED)), true, 50));

        mockMvc.perform(get(LIST))
                .andExpect(content().string(containsString("新しい 50 件だけを示しています。それより前の貨物は、追跡番号を入れて照会してください。")));
    }

    // 追跡番号の入力

    @Test
    void 追跡番号を入れて照会すると空白とハイフンを除き大文字にそろえて照会の結果へ移す() throws Exception {
        // 区切って伝えられた番号も受け付ける（Bolt 27 の開発レビューの判断）
        mockMvc.perform(get(LIST).param("trackingNumber", " ctabc-defgh 2345 "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETAIL));

        then(queryService).should(never()).recent(any());
    }

    @Test
    void 追跡番号の形式に誤りがあればエラー要約と項目の下に理由と直し方を示し入力した値を残す() throws Exception {
        given(queryService.recent(TestActors.SHIPPER_COMPANY))
                .willReturn(new RecentTrackingRecords(List.of(), false, 50));

        mockMvc.perform(get(LIST).param("trackingNumber", "CT-12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>入力内容に誤りがあります - 追跡の照会 - cargo-tracker</title>")))
                .andExpect(content().string(containsString("id=\"error-summary\"")))
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "href=\"#trackingNumber\">追跡番号: CT で始まる 14 文字で入力してください。英字の I・L・O と数字の 0・1 は使いません（例: CTABCDEFGH2345）</a>")))
                .andExpect(content().string(containsString("value=\"CT-12\"")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));
    }

    @Test
    void 追跡番号が空のまま照会すると入力してくださいと示す() throws Exception {
        given(queryService.recent(TestActors.SHIPPER_COMPANY))
                .willReturn(new RecentTrackingRecords(List.of(), false, 50));

        mockMvc.perform(get(LIST).param("trackingNumber", "  "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"#trackingNumber\">追跡番号: 入力してください</a>")));
    }

    // 照会の結果

    @Test
    void 照会の結果に現在の状態と到着予定と予定区間と主要実績と出典と取得時刻を荷主の日時表示で示す() throws Exception {
        given(queryService.find(TrackingFixture.TRACKING_NUMBER, TestActors.SHIPPER_COMPANY))
                .willReturn(Optional.of(view(List.of(pickup()))));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>追跡の照会 CTABCDEFGH2345</h1>")))
                .andExpect(content().string(containsString("<dd class=\"col-sm-9\">集荷済み</dd>")))
                .andExpect(content().string(containsString("2026-11-15 09:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("2026-11-16 12:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("<ol aria-labelledby=\"schedule-heading\"")))
                .andExpect(content().string(containsString("区間 1、航海 V100、JPTYO から KRPUS")))
                .andExpect(content().string(containsString("出発予定 2026-11-01 09:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("<ul aria-labelledby=\"milestones-heading\"")))
                .andExpect(content().string(containsString("集荷、JPTYO")))
                .andExpect(content().string(containsString("発生 2026-11-01 11:30 Asia/Tokyo（UTC+09:00）")))
                // 出典と取得時刻は行を分ける（括弧の二重を避ける。Bolt 27 の開発レビュー）
                .andExpect(content().string(containsString("<span>出典 現場記録 F-118</span>")))
                .andExpect(content().string(containsString("<span>取得 2026-11-01 12:00 Asia/Tokyo（UTC+09:00）</span>")))
                .andExpect(content().string(containsString("href=\"/customer/tracking-records\">追跡の照会へ戻る</a>")));
    }

    @Test
    void 照会の結果の日時にUTCを併記しない() throws Exception {
        given(queryService.find(TrackingFixture.TRACKING_NUMBER, TestActors.SHIPPER_COMPANY))
                .willReturn(Optional.of(view(List.of(pickup()))));

        String body = mockMvc.perform(get(DETAIL)).andReturn().getResponse().getContentAsString();

        // UTC の併記は社内の日時表示（Bolt 27 計画の確認ポイント 11）。経路版・実績番号・実績の状態を照会結果が持たないことは
        // 集約のテスト（TrackingRecordCustomerViewTest）で確かめる
        assertThat(body).doesNotContain("（UTC 2026");
    }

    @Test
    void 主要実績がなければまだないと示す() throws Exception {
        given(queryService.find(TrackingFixture.TRACKING_NUMBER, TestActors.SHIPPER_COMPANY))
                .willReturn(Optional.of(view(List.of())));

        mockMvc.perform(get(DETAIL)).andExpect(content().string(containsString("主要実績はまだありません。")));
    }

    @Test
    void 他社や存在しない追跡番号と形式の誤った追跡番号は404で本文も同じ() throws Exception {
        given(queryService.find(any(), any())).willReturn(Optional.empty());

        String notFound = mockMvc.perform(get(DETAIL))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String malformed = mockMvc.perform(get(LIST + "/not-a-tracking-number"))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 打ち間違いが日常の経路なので、既定のエラーページではなく C-10 の案内と戻り方を示す（Bolt 27 の開発レビューの判断）
        assertThat(notFound)
                .contains("<title>追跡番号が見つかりません - 追跡の照会 - cargo-tracker</title>")
                .contains("<h1>追跡番号が見つかりません</h1>")
                .contains("この追跡番号の貨物は見つかりませんでした。追跡番号に誤りがないか確かめてください。")
                .contains("自社の貨物でない追跡番号は照会できません。")
                .contains("href=\"/customer/tracking-records\">追跡の照会へ戻る</a>");
        assertThat(malformed).isEqualTo(notFound);
        then(queryService).should().find(TrackingFixture.TRACKING_NUMBER, TestActors.SHIPPER_COMPANY);
    }
}
