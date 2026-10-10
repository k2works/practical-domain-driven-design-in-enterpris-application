package com.example.cargotracker.tracking.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingRecordCommandService;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordQueryService;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** S-11 追跡一覧と S-12 追跡の詳細（追跡管理者。Bolt 26。主要実績の一覧と登録の結果は Bolt 26c）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.TRACKING_MANAGER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = TrackingRecordController.class)
@Import(MilestoneRegistrationControllerTest.FixedClock.class)
class TrackingRecordControllerTest {

    private static final String LIST = "/staff/tracking-records";
    private static final String DETAIL = "/staff/tracking-records/" + TrackingFixture.TRACKING_NUMBER.value();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TrackingRecordQueryService queryService;

    @MockitoBean
    TrackingRecordCommandService commandService;

    private static TrackingRecordSummary summary(String trackingNumber, TrackingStatus status) {
        return new TrackingRecordSummary(
                new TrackingNumber(trackingNumber),
                status,
                TrackingFixture.at("2026-11-15T00:00:00Z"),
                TrackingFixture.at("2026-10-26T01:00:00Z"));
    }

    private static TrackingRecord trackingRecord(TrackingStatus status) {
        return trackingRecord(status, List.of());
    }

    private static TrackingRecord trackingRecord(TrackingStatus status, List<Milestone> milestones) {
        return TrackingRecord.reconstitute(
                TrackingFixture.TRACKING_NUMBER,
                TrackingFixture.BOOKING_ID,
                TrackingFixture.SHIPPER,
                TrackingFixture.CONSIGNEE,
                TrackedBookingStatus.CONFIRMED,
                TrackingFixture.schedule(),
                status,
                milestones.isEmpty() ? OptionalInt.empty() : OptionalInt.of(milestones.size()),
                milestones,
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
                .andExpect(content().string(containsString("区間 1、航海 V100、JPTYO から KRPUS")))
                .andExpect(content().string(containsString("区間 2、航海 V200、KRPUS から USLAX")))
                .andExpect(content()
                        .string(containsString("出発予定 2026-11-04 09:00 Asia/Tokyo（UTC+09:00）（UTC 2026-11-04 00:00）")))
                .andExpect(content()
                        .string(containsString("到着予定 2026-11-15 09:00 Asia/Tokyo（UTC+09:00）（UTC 2026-11-15 00:00）")))
                .andExpect(content().string(not(containsString("→"))))
                .andExpect(content().string(containsString("主要実績はまだありません。実績を登録すると、現在状態が更新されます。")))
                .andExpect(content()
                        .string(containsString(
                                "href=\"/staff/tracking-records/CTABCDEFGH2345/milestones/new\">実績を登録</a>")))
                .andExpect(content().string(containsString("href=\"/staff/tracking-records\">追跡一覧</a>")));
    }

    // S-12 の主要実績（Bolt 26c）

    private static Milestone milestone(
            int no,
            MilestoneKind kind,
            String occurredAt,
            SourceKind sourceKind,
            String reference,
            MilestoneState state) {
        return new Milestone(
                no,
                kind,
                new Location("JPTYO"),
                TrackingFixture.at(occurredAt),
                new Source(sourceKind, reference, TrackingFixture.at("2026-11-01T03:00:00Z")),
                state,
                TestActors.STAFF_USER,
                TrackingFixture.at("2026-11-01T03:00:00Z"));
    }

    @Test
    void 主要実績を発生時刻の順のリストで種類と場所と発生時刻と出典と状態とともに示す() throws Exception {
        // 実績 2 は実績 1 より前に起きた（登録の順と発生時刻の順が違う）
        given(queryService.detail(TrackingFixture.TRACKING_NUMBER))
                .willReturn(Optional.of(trackingRecord(
                        TrackingStatus.RECEIVED_AT_ORIGIN,
                        List.of(
                                milestone(
                                        1,
                                        MilestoneKind.RECEIPT_AT_ORIGIN,
                                        "2026-11-01T02:50:00Z",
                                        SourceKind.FIELD_RECORD,
                                        "F-119",
                                        MilestoneState.ADOPTED),
                                milestone(
                                        2,
                                        MilestoneKind.PICKUP,
                                        "2026-11-01T02:30:00Z",
                                        SourceKind.INTERNAL_CHECK,
                                        "IC-7",
                                        MilestoneState.ADOPTED)))));

        String body = mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<ol aria-labelledby=\"milestones-heading\"")))
                .andExpect(content().string(containsString("id=\"milestone-1\"")))
                .andExpect(content().string(containsString("実績 2、集荷、JPTYO")))
                .andExpect(content().string(containsString("実績 1、搬入、JPTYO")))
                .andExpect(content()
                        .string(containsString("発生 2026-11-01 11:30 Asia/Tokyo（UTC+09:00）（UTC 2026-11-01 02:30）")))
                .andExpect(content().string(containsString("出典 社内確認 IC-7（取得 2026-11-01 12:00 Asia/Tokyo")))
                .andExpect(content().string(containsString("状態 採用")))
                .andExpect(content().string(not(containsString("主要実績はまだありません。"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body.indexOf("実績 2、集荷")).isLessThan(body.indexOf("実績 1、搬入"));
    }

    @Test
    void 登録の結果と既存の実績へのリンクを状態の通知として示す() throws Exception {
        given(queryService.detail(TrackingFixture.TRACKING_NUMBER))
                .willReturn(Optional.of(trackingRecord(TrackingStatus.PICKUP_SCHEDULED)));

        mockMvc.perform(get(DETAIL)
                        .flashAttr("result", "出典（現場記録 F-118）の実績はすでに登録されています（実績 1）。")
                        .flashAttr("resultLinkHref", "#milestone-1")
                        .flashAttr("resultLinkLabel", "実績 1 を一覧で見る"))
                .andExpect(
                        content().string(containsString("<p role=\"status\">出典（現場記録 F-118）の実績はすでに登録されています（実績 1）。</p>")))
                .andExpect(content().string(containsString("href=\"#milestone-1\">実績 1 を一覧で見る</a>")));
    }

    @Test
    void 競合を警告として示す() throws Exception {
        given(queryService.detail(TrackingFixture.TRACKING_NUMBER))
                .willReturn(Optional.of(trackingRecord(TrackingStatus.PICKUP_SCHEDULED)));

        mockMvc.perform(get(DETAIL).flashAttr("problem", "ほかの追跡管理者が先にこの追跡記録を更新しました。"))
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("ほかの追跡管理者が先にこの追跡記録を更新しました。")));
    }

    @ParameterizedTest
    @EnumSource(MilestoneKind.class)
    void すべての実績の種類に表示名がある(MilestoneKind kind) {
        org.assertj.core.api.Assertions.assertThat(TrackingRecordViews.kind(kind))
                .isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"EXTERNAL_RECORD, 外部原本", "FIELD_RECORD, 現場記録", "INTERNAL_CHECK, 社内確認", "MANUAL_ENTRY, 手動入力"})
    void 出典の種類は表示名で示す(SourceKind sourceKind, String name) {
        org.assertj.core.api.Assertions.assertThat(TrackingRecordViews.sourceKind(sourceKind))
                .isEqualTo(name);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT, 下書き", "ADOPTED, 採用", "UNDER_REVIEW, 確認中", "RETAINED_ONLY, 保持のみ"})
    void 実績の状態は表示名で示す(MilestoneState state, String name) {
        org.assertj.core.api.Assertions.assertThat(TrackingRecordViews.milestoneState(state))
                .isEqualTo(name);
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
