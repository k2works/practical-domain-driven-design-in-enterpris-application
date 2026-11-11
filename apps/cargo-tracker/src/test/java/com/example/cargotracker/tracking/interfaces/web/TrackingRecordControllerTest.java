package com.example.cargotracker.tracking.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commandservices.MilestoneAdoptionOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingRecordCommandService;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordQueryService;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.ExternalMilestoneReceipt;
import com.example.cargotracker.tracking.domain.model.aggregates.MilestoneAdoption;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

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

    @MockitoBean
    TrackingOutOfScopeResponder outOfScope;

    private static TrackingRecordSummary summary(String trackingNumber, TrackingStatus status) {
        return new TrackingRecordSummary(
                new TrackingNumber(trackingNumber),
                status,
                TrackingFixture.at("2026-11-15T00:00:00Z"),
                TrackingFixture.at("2026-11-16T03:00:00Z"),
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
        given(queryService.recent(any()))
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
        given(queryService.recent(any())).willReturn(new RecentTrackingRecords(List.of(), false, 50));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("追跡記録はまだありません。営業担当者が本予約を確定すると、追跡が始まった貨物がここに出ます。")))
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    void 上限を超えたら新しい件数だけを示していると示す() throws Exception {
        given(queryService.recent(any()))
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
        "DELIVERED, 引渡し済み"
    })
    // 確認中は状態バッジで示す（Bolt 39gc の行 22・23。下の Bolt39gc_確認中の表示）
    void 追跡状態は表示名で示す(TrackingStatus status, String displayName) throws Exception {
        given(queryService.recent(any()))
                .willReturn(new RecentTrackingRecords(List.of(summary("CTABCDEFGH2345", status)), false, 50));
        given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any()))
                .willReturn(Optional.of(trackingRecord(status)));

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
        given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any()))
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
                // 社内の S-12 は「区間 n、」のまま（Bolt 37f3 の行 2。荷主の C-10 だけ「航海 …」にした）
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
        given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any()))
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
                .andExpect(content().string(containsString("<ul aria-labelledby=\"milestones-heading\"")))
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
    void 発生時刻が同じ主要実績は実績番号の順に示す() throws Exception {
        given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any()))
                .willReturn(Optional.of(trackingRecord(
                        TrackingStatus.RECEIVED_AT_ORIGIN,
                        List.of(
                                milestone(
                                        1,
                                        MilestoneKind.PICKUP,
                                        "2026-11-01T02:30:00Z",
                                        SourceKind.FIELD_RECORD,
                                        "F-118",
                                        MilestoneState.ADOPTED),
                                milestone(
                                        2,
                                        MilestoneKind.RECEIPT_AT_ORIGIN,
                                        "2026-11-01T02:30:00Z",
                                        SourceKind.FIELD_RECORD,
                                        "F-119",
                                        MilestoneState.ADOPTED)))));

        String body = mockMvc.perform(get(DETAIL)).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body.indexOf("実績 1、集荷")).isLessThan(body.indexOf("実績 2、搬入"));
    }

    @Test
    void 登録の結果と既存の実績へのリンクを状態の通知として示す() throws Exception {
        given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any()))
                .willReturn(Optional.of(trackingRecord(TrackingStatus.PICKUP_SCHEDULED)));

        mockMvc.perform(get(DETAIL)
                        .flashAttr("result", "出典（現場記録 F-118）の実績はすでに登録されています（実績 1）。今回の入力は登録していません。")
                        .flashAttr("resultLinkHref", "#milestone-1")
                        .flashAttr("resultLinkLabel", "実績 1 を一覧で見る"))
                .andExpect(content()
                        .string(containsString(
                                "<p role=\"status\">出典（現場記録 F-118）の実績はすでに登録されています（実績 1）。今回の入力は登録していません。</p>")))
                .andExpect(content().string(containsString("href=\"#milestone-1\">実績 1 を一覧で見る</a>")));
    }

    @Test
    void 競合を警告として示す() throws Exception {
        given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any()))
                .willReturn(Optional.of(trackingRecord(TrackingStatus.PICKUP_SCHEDULED)));

        mockMvc.perform(get(DETAIL).flashAttr("problem", "ほかの更新が先にこの追跡記録を変更しました。最新の主要実績を確かめ、必要なら、もう一度登録してください。"))
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("ほかの更新が先にこの追跡記録を変更しました。最新の主要実績を確かめ、必要なら、もう一度登録してください。")));
    }

    /** Bolt 39g の行 42（決定 (8)）。DE-13 の下書きは形を変えずに示し、現在状態は変えない。 */
    @Nested
    class Bolt39g_外部原本の下書き {

        @Test
        void 行42_下書きの実績を状態と外部原本の出典とともに示し現在状態は集荷予定のまま() throws Exception {
            UtcInstant published = TrackingFixture.at("2026-11-19T15:00:00Z");
            Milestone draft = new Milestone(
                    1,
                    MilestoneKind.DEPARTURE,
                    new Location("JPTYO"),
                    TrackingFixture.at("2026-11-19T14:00:00Z"),
                    new Source(
                            SourceKind.EXTERNAL_RECORD,
                            "9f1c0000-0000-0000-0000-000000000001",
                            TrackingFixture.at("2026-11-19T14:30:00Z")),
                    MilestoneState.DRAFT,
                    TestActors.STAFF_USER,
                    published,
                    new ExternalOrigin(UUID.randomUUID(), "MS-0301", published));
            TrackingRecord withDraft = TrackingRecord.reconstitute(
                    TrackingFixture.TRACKING_NUMBER,
                    TrackingFixture.BOOKING_ID,
                    TrackingFixture.SHIPPER,
                    TrackingFixture.CONSIGNEE,
                    TrackedBookingStatus.CONFIRMED,
                    TrackingFixture.schedule(),
                    TrackingStatus.PICKUP_SCHEDULED,
                    OptionalInt.empty(),
                    List.of(draft),
                    TrackingFixture.at("2026-11-15T00:00:00Z"),
                    TrackingFixture.at("2026-11-16T03:00:00Z"),
                    TrackingFixture.STARTED_AT,
                    1);
            given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any()))
                    .willReturn(Optional.of(withDraft));

            mockMvc.perform(get(DETAIL))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("実績 1、出発、JPTYO")))
                    .andExpect(content().string(containsString("出典 外部原本 9f1c0000-0000-0000-0000-000000000001（取得 ")))
                    .andExpect(content().string(containsString("状態 下書き")))
                    .andExpect(content().string(containsString("集荷予定")))
                    .andExpect(content().string(not(containsString("状態 採用"))));
        }
    }

    /** Bolt 39gb の行 43・51〜55・50・57（S-12 の候補の表示と採用の POST）。 */
    @Nested
    class Bolt39gb_下書きの候補と採用 {

        private static final String ADOPT = DETAIL + "/milestones/2/adopt";
        private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-0000039b0c01");

        private Milestone external(
                int no, Location location, String occurredAt, String publishedAt, MilestoneState state) {
            UtcInstant published = TrackingFixture.at(publishedAt);
            boolean adopted = state == MilestoneState.ADOPTED;
            return new Milestone(
                    no,
                    MilestoneKind.DEPARTURE,
                    location,
                    TrackingFixture.at(occurredAt),
                    new Source(SourceKind.EXTERNAL_RECORD, "r-" + no, published),
                    state,
                    TestActors.STAFF_USER,
                    published,
                    new ExternalOrigin(SOURCE_ID, "MS-0301", published),
                    adopted ? TestActors.STAFF_USER : null,
                    adopted ? published : null);
        }

        private TrackingRecord withMilestones(TrackedBookingStatus bookingStatus, List<Milestone> milestones) {
            return TrackingRecord.reconstitute(
                    TrackingFixture.TRACKING_NUMBER,
                    TrackingFixture.BOOKING_ID,
                    TrackingFixture.SHIPPER,
                    TrackingFixture.CONSIGNEE,
                    bookingStatus,
                    TrackingFixture.schedule(),
                    TrackingStatus.PICKUP_SCHEDULED,
                    OptionalInt.empty(),
                    milestones,
                    TrackingFixture.at("2026-11-15T00:00:00Z"),
                    TrackingFixture.at("2026-11-16T03:00:00Z"),
                    TrackingFixture.STARTED_AT,
                    7);
        }

        private String detailOf(TrackingRecord trackingRecord) throws Exception {
            given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any()))
                    .willReturn(Optional.of(trackingRecord));
            return mockMvc.perform(get(DETAIL))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        }

        @Test
        void 行43_置き換えられた下書きに文を示し候補には採用のボタンを出す() throws Exception {
            String body = detailOf(withMilestones(
                    TrackedBookingStatus.CONFIRMED,
                    List.of(
                            external(
                                    1,
                                    new Location("JPTYO"),
                                    "2026-10-30T00:00:00Z",
                                    "2026-10-31T00:00:00Z",
                                    MilestoneState.DRAFT),
                            external(
                                    2,
                                    new Location("KRPUS"),
                                    "2026-10-30T01:00:00Z",
                                    "2026-10-31T01:00:00Z",
                                    MilestoneState.DRAFT))));

            org.assertj.core.api.Assertions.assertThat(body)
                    .containsOnlyOnce("後から公開された原本に置き換えられました")
                    .contains("action=\"/staff/tracking-records/CTABCDEFGH2345/milestones/2/adopt\"")
                    .contains("name=\"expectedVersion\" value=\"7\"")
                    .contains("実績 2 を採用する")
                    .doesNotContain("実績 1 を採用する");
            org.assertj.core.api.Assertions.assertThat(body.indexOf("後から公開された原本に置き換えられました"))
                    .isLessThan(body.indexOf("実績 2、出発"));
        }

        @Test
        void 行43_元の値の原本が後から公開されると表示が変わる() throws Exception {
            String body = detailOf(withMilestones(
                    TrackedBookingStatus.CONFIRMED,
                    List.of(
                            external(
                                    1,
                                    new Location("JPTYO"),
                                    "2026-10-30T00:00:00Z",
                                    "2026-10-31T00:00:00Z",
                                    MilestoneState.DRAFT),
                            external(
                                    2,
                                    new Location("KRPUS"),
                                    "2026-10-30T01:00:00Z",
                                    "2026-10-31T01:00:00Z",
                                    MilestoneState.DRAFT),
                            external(
                                    3,
                                    new Location("JPTYO"),
                                    "2026-10-30T00:00:00Z",
                                    "2026-10-31T02:00:00Z",
                                    MilestoneState.DRAFT))));

            org.assertj.core.api.Assertions.assertThat(body)
                    .contains("実績 3 を採用する")
                    .doesNotContain("実績 2 を採用する");
        }

        @Test
        void 行52_採用済みと値が違う候補には採用を出さず理由を示す() throws Exception {
            String body = detailOf(withMilestones(
                    TrackedBookingStatus.CONFIRMED,
                    List.of(
                            external(
                                    1,
                                    new Location("JPTYO"),
                                    "2026-10-30T00:00:00Z",
                                    "2026-10-31T00:00:00Z",
                                    MilestoneState.ADOPTED),
                            external(
                                    2,
                                    new Location("KRPUS"),
                                    "2026-10-30T01:00:00Z",
                                    "2026-10-31T01:00:00Z",
                                    MilestoneState.DRAFT))));

            org.assertj.core.api.Assertions.assertThat(body)
                    .contains("採用済みの実績と値が違います")
                    .doesNotContain("を採用する")
                    .doesNotContain("置き換えられました");
        }

        @Test
        void 行53_採用済みと同じ値の候補には採用は要らないと示す() throws Exception {
            String body = detailOf(withMilestones(
                    TrackedBookingStatus.CONFIRMED,
                    List.of(
                            external(
                                    1,
                                    new Location("JPTYO"),
                                    "2026-10-30T00:00:00Z",
                                    "2026-10-31T00:00:00Z",
                                    MilestoneState.ADOPTED),
                            external(
                                    2,
                                    new Location("JPTYO"),
                                    "2026-10-30T00:00:00Z",
                                    "2026-10-31T01:00:00Z",
                                    MilestoneState.DRAFT))));

            org.assertj.core.api.Assertions.assertThat(body)
                    .contains("採用済みの実績と同じ値です")
                    .doesNotContain("を採用する");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "取消済み, CANCELLED, 2026-10-30T00:00:00Z, 取消済みの予約の実績は採用できません",
            "完了後, COMPLETED, 2026-10-30T00:00:00Z, 完了した追跡の実績は採用できません",
            "未来の発生時刻, CONFIRMED, 2026-11-01T03:00:01Z, 発生時刻がいまより後の実績は採用できません"
        })
        void 行54_55_採用できない候補には採用を出さず理由を示す(
                String name, TrackedBookingStatus bookingStatus, String occurredAt, String reason) throws Exception {
            String body = detailOf(withMilestones(
                    bookingStatus,
                    List.of(external(
                            1, new Location("JPTYO"), occurredAt, "2026-10-31T00:00:00Z", MilestoneState.DRAFT))));

            org.assertj.core.api.Assertions.assertThat(body).contains(reason).doesNotContain("を採用する");
        }

        @Test
        void 行55_発生時刻がいまと同時刻の候補は採用できる() throws Exception {
            String body = detailOf(withMilestones(
                    TrackedBookingStatus.CONFIRMED,
                    List.of(external(
                            1,
                            new Location("JPTYO"),
                            "2026-11-01T03:00:00Z",
                            "2026-10-31T00:00:00Z",
                            MilestoneState.DRAFT))));

            org.assertj.core.api.Assertions.assertThat(body).contains("実績 1 を採用する");
        }

        @Test
        void 行50_採用したら結果と導出し直した現在状態を示して追跡の詳細に戻る() throws Exception {
            given(commandService.adoptCandidate(eq(TrackingFixture.TRACKING_NUMBER), eq(2), eq(7L), any()))
                    .willReturn(
                            new MilestoneAdoptionOutcome.Adopted(2, MilestoneState.ADOPTED, TrackingStatus.IN_TRANSIT));

            mockMvc.perform(post(ADOPT).param("expectedVersion", "7"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(DETAIL))
                    .andExpect(flash().attribute("result", "実績 2 を採用しました。現在状態は「輸送中」です。"));
        }

        @Test
        void 行53_採用済みと同じ値なら採用は要らないと結果に示す() throws Exception {
            given(commandService.adoptCandidate(any(), eq(2), eq(7L), any()))
                    .willReturn(new MilestoneAdoptionOutcome.SameAsAdopted(1));

            mockMvc.perform(post(ADOPT).param("expectedVersion", "7"))
                    .andExpect(redirectedUrl(DETAIL))
                    .andExpect(flash().attribute("result", "実績 2 は採用済みの実績 1 と同じ値です。採用は要りません。"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "NOT_CANDIDATE, 実績 2 は候補ではありません。最新の主要実績を確かめてください。",
            "CONFLICTS_WITH_ADOPTED, 実績 2 は採用できません。採用済みの実績と値が違います。",
            "BOOKING_CANCELLED, 実績 2 は採用できません。取消済みの予約の実績は採用できません。",
            "TRACKING_COMPLETED, 実績 2 は採用できません。完了した追跡の実績は採用できません。",
            "OCCURRED_IN_FUTURE, 実績 2 は採用できません。発生時刻がいまより後の実績は採用できません。"
        })
        void 行51_54_55_拒否の理由を警告で示す(AdoptionRejectionReason reason, String message) throws Exception {
            given(commandService.adoptCandidate(any(), eq(2), eq(7L), any()))
                    .willReturn(new MilestoneAdoptionOutcome.Rejected(reason));

            mockMvc.perform(post(ADOPT).param("expectedVersion", "7"))
                    .andExpect(redirectedUrl(DETAIL))
                    .andExpect(flash().attribute("problem", message));
        }

        @Test
        void 行57_版の不一致は読み直しを促す() throws Exception {
            given(commandService.adoptCandidate(any(), eq(2), eq(7L), any()))
                    .willReturn(new MilestoneAdoptionOutcome.Conflict());

            mockMvc.perform(post(ADOPT).param("expectedVersion", "7"))
                    .andExpect(redirectedUrl(DETAIL))
                    .andExpect(flash().attribute("problem", "ほかの更新が先にこの追跡記録を変更しました。最新の主要実績を確かめ、必要なら、もう一度採用してください。"));
        }

        @Test
        void ない追跡記録の採用は404() throws Exception {
            given(commandService.adoptCandidate(any(), eq(2), eq(7L), any()))
                    .willReturn(new MilestoneAdoptionOutcome.NotFound());

            mockMvc.perform(post(ADOPT).param("expectedVersion", "7")).andExpect(status().isNotFound());
        }

        @Test
        void 形式の誤った追跡番号の採用は404でコマンドを呼ばない() throws Exception {
            mockMvc.perform(post("/staff/tracking-records/not-a-tracking-number/milestones/2/adopt")
                            .param("expectedVersion", "7"))
                    .andExpect(status().isNotFound());

            then(commandService).should(never()).adoptCandidate(any(), anyInt(), anyLong(), any());
        }

        @Test
        void 範囲の外の採用は記録して404でコマンドを呼ばない() throws Exception {
            given(queryService.outOfScope(eq(TrackingFixture.TRACKING_NUMBER), any()))
                    .willReturn(true);
            given(outOfScope.recordAndNotFound(any(), eq(TrackingFixture.TRACKING_NUMBER.value())))
                    .willReturn(new ResponseStatusException(HttpStatus.NOT_FOUND));

            mockMvc.perform(post(ADOPT).param("expectedVersion", "7")).andExpect(status().isNotFound());

            then(commandService).should(never()).adoptCandidate(any(), anyInt(), anyLong(), any());
        }

        @Test
        void 採用の途中で範囲の外になったら記録して404() throws Exception {
            given(commandService.adoptCandidate(any(), eq(2), eq(7L), any()))
                    .willReturn(new MilestoneAdoptionOutcome.OutOfScope());
            given(outOfScope.recordAndNotFound(any(), eq(TrackingFixture.TRACKING_NUMBER.value())))
                    .willReturn(new ResponseStatusException(HttpStatus.NOT_FOUND));

            mockMvc.perform(post(ADOPT).param("expectedVersion", "7")).andExpect(status().isNotFound());

            then(outOfScope).should().recordAndNotFound(any(), eq(TrackingFixture.TRACKING_NUMBER.value()));
        }
    }

    /** Bolt 39gc の行 13〜15・22・23（S-11・S-12 の確認中の表示と採用の POST の文）。 */
    @Nested
    class Bolt39gc_確認中の表示 {

        private static final String ADOPT = DETAIL + "/milestones/2/adopt";
        private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-0000039c0c01");
        private static final String BADGE =
                "<span class=\"badge text-bg-warning\"><span aria-hidden=\"true\">⚠ </span><span>確認中</span></span>";

        private ExternalMilestone external(String location, String publishedAt) {
            return new ExternalMilestone(
                    TrackingFixture.TRACKING_NUMBER,
                    MilestoneKind.DEPARTURE,
                    new Location(location),
                    TrackingFixture.at("2026-10-30T06:00:00Z"),
                    new Source(SourceKind.EXTERNAL_RECORD, "r-" + publishedAt, TrackingFixture.at(publishedAt)),
                    new ExternalOrigin(SOURCE_ID, "MS-0302", TrackingFixture.at(publishedAt)),
                    TestActors.STAFF_USER);
        }

        private TrackingRecord receive(TrackingRecord trackingRecord, String location, String publishedAt) {
            return ((ExternalMilestoneReceipt.Received) trackingRecord.receiveExternalMilestone(
                            external(location, publishedAt), TrackingFixture.at(publishedAt)))
                    .trackingRecord();
        }

        /** 到着を手入力で採用した（10-30T00:00）後に、より後の発生時刻の出発の下書き（実績 2）が届いた。採用すると時系列に反する。 */
        private TrackingRecord arrivedThenDraft() {
            TrackingRecord arrived = TrackingRecord.start(
                            TrackingFixture.TRACKING_NUMBER,
                            TrackingFixture.BOOKING_ID,
                            TrackingFixture.SHIPPER,
                            TrackingFixture.CONSIGNEE,
                            TrackingFixture.schedule(),
                            TrackingFixture.STARTED_AT)
                    .trackingRecord()
                    .registerMilestone(
                            MilestoneKind.ARRIVAL,
                            new Location("USLAX"),
                            TrackingFixture.at("2026-10-30T00:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-1", TrackingFixture.at("2026-10-31T00:00:00Z")),
                            TestActors.STAFF_USER,
                            TrackingFixture.at("2026-10-31T00:00:00Z"))
                    .trackingRecord();
            return receive(arrived, "JPTYO", "2026-10-31T01:00:00Z");
        }

        private TrackingRecord underReview() {
            return ((MilestoneAdoption.Adopted) arrivedThenDraft()
                            .adoptCandidate(2, TestActors.STAFF_USER, TrackingFixture.at("2026-10-31T02:00:00Z")))
                    .trackingRecord();
        }

        private String detailOf(TrackingRecord trackingRecord) throws Exception {
            given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any()))
                    .willReturn(Optional.of(trackingRecord));
            return mockMvc.perform(get(DETAIL))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        }

        @Test
        void 行15_採用すると確認中になる候補にも採用のボタンを出し注記を添える() throws Exception {
            String body = detailOf(arrivedThenDraft());

            org.assertj.core.api.Assertions.assertThat(body)
                    .contains("実績 2 を採用する")
                    .containsOnlyOnce("採用すると、時系列に反するため確認中になります");
        }

        @Test
        void 行23_現在状態は確認中のバッジと理由の文と確認中の実績の状態を示す() throws Exception {
            String body = detailOf(underReview());

            org.assertj.core.api.Assertions.assertThat(body)
                    .contains(BADGE)
                    .contains("時系列に反する実績があります（実績 2）。採用済みの実績から導いた状態は 目的地到着 です。")
                    .contains("状態 確認中")
                    .doesNotContain("を採用する");
        }

        @Test
        void 行13_確認中の実績と値が違う新しい下書きには採用を出さず理由を示す() throws Exception {
            String body = detailOf(receive(underReview(), "KRPUS", "2026-10-31T03:00:00Z"));

            org.assertj.core.api.Assertions.assertThat(body)
                    .contains("確認中の実績と値が違います")
                    .doesNotContain("採用済みの実績と値が違います")
                    .doesNotContain("を採用する");
        }

        @Test
        void 行14_確認中の実績と同じ値の新しい下書きには採用は要らないと示す() throws Exception {
            String body = detailOf(receive(underReview(), "JPTYO", "2026-10-31T03:00:00Z"));

            org.assertj.core.api.Assertions.assertThat(body)
                    .contains("確認中の実績と同じ値です")
                    .doesNotContain("を採用する");
        }

        @Test
        void 行22_追跡一覧は確認中の追跡の現在状態をバッジで示し他は文字のまま() throws Exception {
            given(queryService.recent(any()))
                    .willReturn(new RecentTrackingRecords(
                            List.of(
                                    summary("CTABCDEFGH2345", TrackingStatus.UNDER_REVIEW),
                                    summary("CTBBBBBBBBBBBB", TrackingStatus.IN_TRANSIT)),
                            false,
                            50));

            mockMvc.perform(get(LIST))
                    .andExpect(content().string(containsString("<td>" + BADGE + "</td>")))
                    .andExpect(content().string(containsString("<td>輸送中</td>")));
        }

        @Test
        void 行6_時系列に反して確認中にした採用の結果を示す() throws Exception {
            given(commandService.adoptCandidate(eq(TrackingFixture.TRACKING_NUMBER), eq(2), eq(0L), any()))
                    .willReturn(new MilestoneAdoptionOutcome.Adopted(
                            2, MilestoneState.UNDER_REVIEW, TrackingStatus.UNDER_REVIEW));

            mockMvc.perform(post(ADOPT).param("expectedVersion", "0"))
                    .andExpect(redirectedUrl(DETAIL))
                    .andExpect(flash().attribute("result", "実績 2 を採用しました。時系列に反するため確認中にしました。"));
        }

        @Test
        void 行13_確認中の実績と値が違う採用の拒否を示す() throws Exception {
            given(commandService.adoptCandidate(any(), eq(2), eq(0L), any()))
                    .willReturn(new MilestoneAdoptionOutcome.Rejected(
                            AdoptionRejectionReason.CONFLICTS_WITH_ADOPTED, Optional.of(MilestoneState.UNDER_REVIEW)));

            mockMvc.perform(post(ADOPT).param("expectedVersion", "0"))
                    .andExpect(redirectedUrl(DETAIL))
                    .andExpect(flash().attribute("problem", "実績 2 は採用できません。確認中の実績と値が違います。"));
        }
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
        given(queryService.detail(eq(TrackingFixture.TRACKING_NUMBER), any())).willReturn(Optional.empty());

        mockMvc.perform(get(DETAIL)).andExpect(status().isNotFound());
    }

    @Test
    void 形式の誤った追跡番号の追跡の詳細は404() throws Exception {
        mockMvc.perform(get("/staff/tracking-records/not-a-tracking-number")).andExpect(status().isNotFound());
    }
}
