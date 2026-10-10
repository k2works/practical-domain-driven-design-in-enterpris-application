package com.example.cargotracker.tracking.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordQueryService;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** S-11 追跡一覧と S-12 追跡の詳細（追跡管理者。Bolt 26）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.TRACKING_MANAGER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = TrackingRecordController.class)
class TrackingRecordControllerTest {

    private static final String LIST = "/staff/tracking-records";
    private static final String DETAIL = "/staff/tracking-records/" + TrackingFixture.TRACKING_NUMBER.value();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TrackingRecordQueryService queryService;

    private static TrackingRecordSummary summary(String trackingNumber, TrackingStatus status) {
        return new TrackingRecordSummary(
                new TrackingNumber(trackingNumber),
                status,
                TrackingFixture.at("2026-11-15T00:00:00Z"),
                TrackingFixture.at("2026-10-26T01:00:00Z"));
    }

    private static TrackingRecord trackingRecord(TrackingStatus status) {
        return TrackingRecord.reconstitute(
                TrackingFixture.TRACKING_NUMBER,
                TrackingFixture.BOOKING_ID,
                TrackingFixture.SHIPPER,
                TrackingFixture.CONSIGNEE,
                TrackedBookingStatus.CONFIRMED,
                TrackingFixture.schedule(),
                status,
                OptionalInt.empty(),
                List.of(),
                TrackingFixture.at("2026-11-15T00:00:00Z"),
                TrackingFixture.at("2026-11-16T03:00:00Z"),
                TrackingFixture.STARTED_AT,
                0);
    }

    // S-11 追跡一覧

    @Test
    void 追跡一覧に追跡番号と現在状態と当初の到着予定と追跡の開始時刻を新しい順に示す() throws Exception {
        given(queryService.recent())
                .willReturn(new RecentTrackingRecords(
                        List.of(
                                summary("CTBBBBBBBBBBBB", TrackingStatus.PICKUP_SCHEDULED),
                                summary("CTABCDEFGH2345", TrackingStatus.PICKUP_SCHEDULED)),
                        false,
                        50));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>追跡一覧</h1>")))
                .andExpect(content()
                        .string(containsString(
                                "<caption id=\"tracking-records-caption\">追跡記録（追跡の開始時刻の新しい順）</caption>")))
                .andExpect(content().string(containsString("<th scope=\"col\">追跡の開始時刻</th>")))
                .andExpect(content()
                        .string(containsString("href=\"/staff/tracking-records/CTBBBBBBBBBBBB\">CTBBBBBBBBBBBB</a>")))
                .andExpect(content().string(containsString("<td>集荷予定</td>")))
                .andExpect(content()
                        .string(containsString("2026-11-15 09:00 Asia/Tokyo（UTC+09:00）（UTC 2026-11-15 00:00）")))
                .andExpect(content()
                        .string(containsString("2026-10-26 10:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-26 01:00）")))
                .andExpect(content().string(matchesPattern("(?s).*CTBBBBBBBBBBBB.*CTABCDEFGH2345.*")))
                .andExpect(content().string(not(containsString("新しい 50 件だけを示しています。"))));
    }

    @Test
    void 追跡記録がなければまだないと示し表を出さない() throws Exception {
        given(queryService.recent()).willReturn(new RecentTrackingRecords(List.of(), false, 50));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("追跡記録はまだありません。営業担当者が本予約を確定すると、追跡が始まった貨物がここに出ます。")))
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    void 上限を超えたら新しい件数だけを示していると示す() throws Exception {
        given(queryService.recent())
                .willReturn(new RecentTrackingRecords(
                        List.of(summary("CTABCDEFGH2345", TrackingStatus.PICKUP_SCHEDULED)), true, 50));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("新しい 50 件だけを示しています。")));
    }

    /** 追跡状態の 10 個の値の表示名（T-57・T-76。ドメインモデルの候補の名前。Bolt 26 計画の確認ポイント 6）。 */
    @ParameterizedTest
    @CsvSource({
        "BOOKED, 予約確定",
        "PICKUP_SCHEDULED, 集荷予定",
        "PICKED_UP, 集荷済み",
        "RECEIVED_AT_ORIGIN, 出発地搬入済み",
        "IN_TRANSIT, 輸送中",
        "TRANSSHIPPING, 積替え中",
        "ARRIVED_AT_DESTINATION, 目的地到着",
        "READY_FOR_DELIVERY, 引渡し可能",
        "DELIVERED, 引渡し済み",
        "UNDER_REVIEW, 確認中"
    })
    void 追跡状態は表示名で示す(TrackingStatus status, String displayName) throws Exception {
        given(queryService.recent())
                .willReturn(new RecentTrackingRecords(List.of(summary("CTABCDEFGH2345", status)), false, 50));
        given(queryService.detail(TrackingFixture.TRACKING_NUMBER)).willReturn(Optional.of(trackingRecord(status)));

        mockMvc.perform(get(LIST)).andExpect(content().string(containsString("<td>" + displayName + "</td>")));
        mockMvc.perform(get(DETAIL))
                .andExpect(content().string(containsString("<dd class=\"col-sm-9\">" + displayName + "</dd>")));
    }

    /** 追跡状態に値を足したときに表示名の抜けを拾う（`@CsvSource` の表だけでは拾えない。Bolt 26 レビュー P-1）。 */
    @ParameterizedTest
    @EnumSource(TrackingStatus.class)
    void すべての追跡状態に表示名がある(TrackingStatus status) {
        org.assertj.core.api.Assertions.assertThat(TrackingRecordViews.status(status))
                .isNotBlank();
    }

    // S-12 追跡の詳細

    @Test
    void 追跡の詳細に現在状態と到着予定と経路版と予定区間と主要実績がまだないことを示す() throws Exception {
        given(queryService.detail(TrackingFixture.TRACKING_NUMBER))
                .willReturn(Optional.of(trackingRecord(TrackingStatus.PICKUP_SCHEDULED)));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>追跡の詳細 CTABCDEFGH2345</h1>")))
                .andExpect(content().string(containsString("<dd class=\"col-sm-9\">CTABCDEFGH2345</dd>")))
                .andExpect(content().string(containsString("<dd class=\"col-sm-9\">集荷予定</dd>")))
                .andExpect(content()
                        .string(containsString("2026-11-15 09:00 Asia/Tokyo（UTC+09:00）（UTC 2026-11-15 00:00）")))
                .andExpect(content()
                        .string(containsString("2026-11-16 12:00 Asia/Tokyo（UTC+09:00）（UTC 2026-11-16 03:00）")))
                .andExpect(content().string(containsString("<dd class=\"col-sm-9\">RC-2026-0001 版 1</dd>")))
                .andExpect(content().string(containsString("<ol aria-labelledby=\"schedule-heading\"")))
                .andExpect(content().string(containsString("区間 1、航海 V100、JPTYO → KRPUS")))
                .andExpect(content().string(containsString("区間 2、航海 V200、KRPUS → USLAX")))
                .andExpect(content()
                        .string(containsString("出発予定 2026-11-04 09:00 Asia/Tokyo（UTC+09:00）（UTC 2026-11-04 00:00）")))
                .andExpect(content()
                        .string(containsString("到着予定 2026-11-15 09:00 Asia/Tokyo（UTC+09:00）（UTC 2026-11-15 00:00）")))
                .andExpect(content().string(containsString("主要実績はまだありません。")))
                .andExpect(content().string(not(containsString("実績を登録"))))
                .andExpect(content().string(containsString("href=\"/staff/tracking-records\">追跡一覧</a>")));
    }

    @Test
    void ない追跡番号の追跡の詳細は404() throws Exception {
        given(queryService.detail(TrackingFixture.TRACKING_NUMBER)).willReturn(Optional.empty());

        mockMvc.perform(get(DETAIL)).andExpect(status().isNotFound());
    }

    @Test
    void 形式の誤った追跡番号の追跡の詳細は404() throws Exception {
        mockMvc.perform(get("/staff/tracking-records/not-a-tracking-number")).andExpect(status().isNotFound());
    }
}
