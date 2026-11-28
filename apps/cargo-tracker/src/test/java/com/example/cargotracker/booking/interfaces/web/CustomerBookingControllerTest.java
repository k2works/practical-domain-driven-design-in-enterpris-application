package com.example.cargotracker.booking.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.booking.application.internal.queryservices.CustomerBookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.RecentBookings;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** C-06 予約一覧（荷主担当者。BR-07。Bolt 27b）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SHIPPER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = CustomerBookingController.class)
class CustomerBookingControllerTest {

    private static final String LIST = "/customer/bookings";
    private static final String TRACKING = "CTABCDEFGH2345";
    private static final String PENDING_TRACKING = "CTKMNPQRS67892";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerBookingQueryService queryService;

    private static RecentBookings.Row row(
            String trackingNumber, String transportRequestNumber, BookingSagaStatus status) {
        return new RecentBookings.Row(
                new TrackingNumber(trackingNumber),
                transportRequestNumber,
                1,
                new UtcInstant(Instant.parse("2026-10-08T00:30:00Z")),
                status);
    }

    private void recentAre(RecentBookings.Row... rows) {
        given(queryService.recent(TestActors.SHIPPER_COMPANY)).willReturn(new RecentBookings(List.of(rows), false, 50));
    }

    @Test
    void 一覧はログインした荷主担当者の企業の予約を追跡番号と見積依頼と確定時刻と追跡で示す() throws Exception {
        recentAre(row(TRACKING, "TR-2026-0002", BookingSagaStatus.COMPLETED));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>予約一覧</h1>")))
                .andExpect(content()
                        .string(containsString("<caption id=\"bookings-caption\">自社の確定した予約（確定時刻の新しい順）</caption>")))
                .andExpect(content().string(containsString("<th scope=\"col\">見積依頼</th>")))
                .andExpect(content().string(containsString("<th scope=\"col\">追跡の状況</th>")))
                // リンクになる追跡番号とならない追跡番号が混ざる理由と、開始待ちが一時的なことを表の前で示す（Bolt 27b の開発レビュー）
                .andExpect(
                        content().string(containsString("追跡が始まった予約は、追跡番号から追跡の照会を開けます。追跡の開始待ちは、しばらくして開き直すと追跡中に変わります。")))
                .andExpect(content().string(not(containsString("件だけを示しています"))))
                // 追跡中なら追跡番号を C-10 の照会の結果へのリンクにする
                .andExpect(content()
                        .string(containsString(
                                "href=\"/customer/tracking-records/" + TRACKING + "\">" + TRACKING + "</a>")))
                // 見積依頼は業務番号を C-04 へのリンクにし、続けて見積り番号を示す
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "href=\"/customer/transport-requests/TR-2026-0002\">TR-2026-0002</a> <span>見積 1</span>")))
                // 荷主の日時表示（UTC を併記しない）
                .andExpect(content().string(containsString("<td>2026-10-08 09:30 Asia/Tokyo（UTC+09:00）</td>")))
                .andExpect(content().string(containsString("<td>追跡中</td>")));
    }

    /** 予約サガの 4 つの状態の荷主の表示（T-57。確認ポイント 4）。追跡中でなければ追跡番号をリンクにしない（C-10 は 404 になるため）。 */
    @ParameterizedTest
    @CsvSource({"IN_PROGRESS, 追跡の開始待ち", "FAILED, 追跡の開始待ち", "NEEDS_HUMAN, 追跡の開始待ち（担当営業が確認します）"})
    void 追跡が始まっていなければ追跡番号をリンクにせず追跡の開始待ちと示す(BookingSagaStatus status, String shown) throws Exception {
        recentAre(row(PENDING_TRACKING, "TR-2026-0003", status));

        String body = mockMvc.perform(get(LIST)).andReturn().getResponse().getContentAsString();

        // 自社の行が出ていることと組にして確かめる（T-85）
        assertThat(body)
                .contains("<td>" + PENDING_TRACKING + "</td>")
                .contains("<td>" + shown + "</td>")
                .doesNotContain("/customer/tracking-records/" + PENDING_TRACKING)
                // 予約サガの状態の英語の値は社内の語（BR-07）
                .doesNotContain(status.name());
    }

    @Test
    void 追跡中の行の画面に予約サガの状態の英語の値を出さない() throws Exception {
        recentAre(row(TRACKING, "TR-2026-0002", BookingSagaStatus.COMPLETED));

        String body = mockMvc.perform(get(LIST)).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("<td>追跡中</td>").doesNotContain("COMPLETED").doesNotContain("（UTC 2026");
    }

    @Test
    void 予約がなければ担当営業の確定を待つ案内を示し表を出さない() throws Exception {
        recentAre();

        mockMvc.perform(get(LIST))
                .andExpect(content().string(containsString("確定した予約はまだありません。担当営業が本予約を確定すると、ここに出ます。")))
                // 承認した見積りの状況を確かめる道（Bolt 27b の開発レビュー）
                .andExpect(content()
                        .string(containsString(
                                "承認した見積りの状況は<a href=\"/customer/transport-requests\">見積依頼の一覧</a>で確かめられます。")))
                .andExpect(content().string(not(containsString("<table"))))
                .andExpect(content().string(not(containsString("追跡が始まった予約は"))));
    }

    @Test
    void 上限を超えたら新しい件数だけを示していると示す() throws Exception {
        given(queryService.recent(TestActors.SHIPPER_COMPANY))
                .willReturn(new RecentBookings(
                        Collections.nCopies(50, row(TRACKING, "TR-2026-0002", BookingSagaStatus.COMPLETED)), true, 50));

        // C-10 の上限の文言と同じく次の一手を示す（Bolt 27b の開発レビュー）
        mockMvc.perform(get(LIST))
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "新しい 50 件だけを示しています。それより前の予約は、追跡番号を入れて<a href=\"/customer/tracking-records\">追跡の照会</a>で確かめてください。")));
    }
}
