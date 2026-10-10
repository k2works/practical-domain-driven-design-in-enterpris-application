package com.example.cargotracker.tracking.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.MilestoneRegistrationOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingRecordCommandService;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordQueryService;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** S-13 主要実績の登録（追跡管理者。US-12 AC1・AC2。Bolt 26c）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.TRACKING_MANAGER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = TrackingRecordController.class)
@Import(MilestoneRegistrationControllerTest.FixedClock.class)
class MilestoneRegistrationControllerTest {

    private static final String DETAIL = "/staff/tracking-records/" + TrackingFixture.TRACKING_NUMBER.value();
    private static final String FORM = DETAIL + "/milestones/new";
    private static final String REGISTER = DETAIL + "/milestones";
    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TrackingRecordQueryService queryService;

    @MockitoBean
    TrackingRecordCommandService commandService;

    @BeforeEach
    void 追跡記録がある() {
        given(queryService.detail(TrackingFixture.TRACKING_NUMBER)).willReturn(Optional.of(trackingRecord(3)));
    }

    private static TrackingRecord trackingRecord(long version) {
        TrackingRecord started = TrackingRecord.start(
                        TrackingFixture.TRACKING_NUMBER,
                        TrackingFixture.BOOKING_ID,
                        TrackingFixture.SHIPPER,
                        TrackingFixture.CONSIGNEE,
                        TrackingFixture.schedule(),
                        TrackingFixture.STARTED_AT)
                .trackingRecord();
        return TrackingRecord.reconstitute(
                started.trackingNumber(),
                started.bookingId(),
                started.shipperCompanyId(),
                started.consigneeCompanyId(),
                started.bookingStatus(),
                started.schedule(),
                started.currentStatus(),
                started.statusBasisMilestoneNo(),
                started.milestones(),
                started.originalEta(),
                started.latestEta(),
                started.startedAt(),
                version);
    }

    private static MockHttpServletRequestBuilder validRegistration() {
        return registration(Map.of());
    }

    /** 正しい入力のうち、指定した項目だけを置き換えて送る（同じ項目を重ねて送らない）。 */
    private static MockHttpServletRequestBuilder registration(String field, String value) {
        return registration(Map.of(field, value));
    }

    private static MockHttpServletRequestBuilder registration(Map<String, String> overrides) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("kind", "PICKUP");
        values.put("location", " jptyo ");
        values.put("occurredAt", "2026-11-01 11:30");
        values.put("sourceKind", "FIELD_RECORD");
        values.put("sourceReference", "  F-118 ");
        values.put("expectedVersion", "3");
        values.putAll(overrides);
        MockHttpServletRequestBuilder request = post(REGISTER);
        values.forEach(request::param);
        return request;
    }

    // 登録の画面（GET）

    @Test
    void 登録の画面に種類と場所と発生時刻と出典の種類と参照の項目と画面を開いたときの版を示す() throws Exception {
        mockMvc.perform(get(FORM))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>主要実績の登録 CTABCDEFGH2345</h1>")))
                .andExpect(content().string(containsString("<legend>種類</legend>")))
                .andExpect(content().string(containsString("積替港での到着は積替、積替港からの出発は出発を選んでください")))
                .andExpect(content().string(containsString("<label for=\"location\">場所</label>")))
                .andExpect(content().string(containsString("例: JPTYO")))
                .andExpect(content().string(containsString("<label for=\"occurredAt\">発生時刻（日本時間）</label>")))
                .andExpect(content().string(containsString("例: 2026-11-01 11:30。タイムゾーンは Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("<legend>出典の種類</legend>")))
                .andExpect(content().string(containsString("<label for=\"sourceReference\">出典の参照</label>")))
                .andExpect(content().string(containsString("name=\"expectedVersion\" value=\"3\"")))
                .andExpect(content().string(containsString(">登録する</button>")))
                .andExpect(content()
                        .string(containsString("href=\"/staff/tracking-records/CTABCDEFGH2345\">追跡の詳細へ戻る</a>")))
                .andExpect(content().string(containsString("/js/transport-request-form.js")));
    }

    @ParameterizedTest
    @CsvSource({
        "PICKUP, 集荷",
        "RECEIPT_AT_ORIGIN, 搬入",
        "DEPARTURE, 出発",
        "TRANSSHIPMENT, 積替",
        "ARRIVAL, 到着",
        "DELIVERY, 引渡し"
    })
    void 実績の種類を6つの表示名で選べる(String value, String name) throws Exception {
        mockMvc.perform(get(FORM))
                .andExpect(content().string(containsString("value=\"" + value + "\"")))
                .andExpect(content().string(containsString(">" + name + "</label>")));
    }

    @Test
    void 出典の種類は現場記録と社内確認と手動入力の3つを選べ外部原本は選べない() throws Exception {
        mockMvc.perform(get(FORM))
                .andExpect(content().string(containsString(">現場記録</label>")))
                .andExpect(content().string(containsString(">社内確認</label>")))
                .andExpect(content().string(containsString(">手動入力</label>")))
                .andExpect(content().string(not(containsString("外部原本"))))
                .andExpect(content().string(not(containsString("value=\"EXTERNAL_RECORD\""))));
    }

    @Test
    void ない追跡番号の登録の画面は404() throws Exception {
        given(queryService.detail(TrackingFixture.TRACKING_NUMBER)).willReturn(Optional.empty());

        mockMvc.perform(get(FORM)).andExpect(status().isNotFound());
    }

    @Test
    void 形式の誤った追跡番号の登録の画面は404() throws Exception {
        mockMvc.perform(get("/staff/tracking-records/not-a-tracking-number/milestones/new"))
                .andExpect(status().isNotFound());
    }

    // 登録（POST）

    @Test
    void 登録すると入力を整えたコマンドを渡し追跡の詳細へ戻って登録した実績を示す() throws Exception {
        given(commandService.registerMilestone(any()))
                .willReturn(new MilestoneRegistrationOutcome.Registered(1, TrackingStatus.PICKED_UP));

        mockMvc.perform(validRegistration())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "実績 1 を登録しました。"));

        ArgumentCaptor<RegisterMilestoneCommand> captor = ArgumentCaptor.forClass(RegisterMilestoneCommand.class);
        then(commandService).should().registerMilestone(captor.capture());
        RegisterMilestoneCommand command = captor.getValue();
        assertThat(command.trackingNumber()).isEqualTo(TrackingFixture.TRACKING_NUMBER);
        assertThat(command.expectedVersion()).isEqualTo(3);
        assertThat(command.kind()).isEqualTo(MilestoneKind.PICKUP);
        // 場所は見積依頼と同じく前後の空白を除いて大文字にそろえ、参照は前後の空白だけを除く（Bolt 26b の P-4）
        assertThat(command.location()).isEqualTo(new Location("JPTYO"));
        assertThat(command.occurredAt()).isEqualTo(new UtcInstant(Instant.parse("2026-11-01T02:30:00Z")));
        assertThat(command.source().kind()).isEqualTo(SourceKind.FIELD_RECORD);
        assertThat(command.source().reference()).isEqualTo("F-118");
        assertThat(command.source().acquiredAt()).isEqualTo(new UtcInstant(NOW));
        assertThat(command.registrant()).isEqualTo(TestActors.STAFF_USER);
    }

    @Test
    void 同じ出典の実績があれば追跡の詳細へ戻り既存の実績を結果として示しその実績へのリンクを添える() throws Exception {
        given(commandService.registerMilestone(any()))
                .willReturn(new MilestoneRegistrationOutcome.AlreadyRegistered(1));

        mockMvc.perform(validRegistration())
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "出典（現場記録 F-118）の実績はすでに登録されています（実績 1）。"))
                .andExpect(flash().attribute("resultLinkHref", "#milestone-1"))
                .andExpect(flash().attribute("resultLinkLabel", "実績 1 を一覧で見る"));
    }

    @Test
    void 画面を開いた後にほかの追跡管理者が更新していたら追跡の詳細へ戻り最新を確かめるよう示す() throws Exception {
        given(commandService.registerMilestone(any())).willReturn(new MilestoneRegistrationOutcome.Conflict());

        mockMvc.perform(validRegistration())
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("problem", "ほかの追跡管理者が先にこの追跡記録を更新しました。最新の主要実績を確かめてください。"));
    }

    @Test
    void 発生時刻が未来なら登録の画面に発生時刻の誤りを示し入力した値を残す() throws Exception {
        given(commandService.registerMilestone(any()))
                .willReturn(new MilestoneRegistrationOutcome.Rejected(MilestoneRejectionReason.OCCURRED_IN_FUTURE));

        mockMvc.perform(validRegistration())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"error-summary\"")))
                .andExpect(content().string(containsString("発生時刻: 発生時刻は現在より前の時刻を入れてください")))
                .andExpect(content().string(containsString("value=\"2026-11-01 11:30\"")))
                .andExpect(content().string(containsString("<title>入力内容に誤りがあります - 主要実績の登録 CTABCDEFGH2345")));
    }

    @Test
    void 追跡記録がなければ404() throws Exception {
        given(commandService.registerMilestone(any())).willReturn(new MilestoneRegistrationOutcome.NotFound());

        mockMvc.perform(validRegistration()).andExpect(status().isNotFound());
    }

    // 入力の誤り（形式。コマンドサービスを呼ばない）

    @Test
    void 何も入れずに登録するとすべての項目の誤りをエラー要約に示しコマンドサービスを呼ばない() throws Exception {
        mockMvc.perform(post(REGISTER).param("expectedVersion", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("入力内容に 5 件の誤りがあります")))
                .andExpect(content().string(containsString("href=\"#kind\">種類: 選択肢から選んでください")))
                .andExpect(content().string(containsString("href=\"#location\">場所: ")))
                .andExpect(content().string(containsString("href=\"#occurredAt\">発生時刻: ")))
                .andExpect(content().string(containsString("href=\"#sourceKind\">出典の種類: 選択肢から選んでください")))
                .andExpect(content().string(containsString("href=\"#sourceReference\">出典の参照: ")));

        then(commandService).should(never()).registerMilestone(any());
    }

    @ParameterizedTest
    @CsvSource({
        "location, TOKYO1, 'UN/LOCODE（国コード 2 文字 + 地点コード 3 文字、例: JPTYO）で入力してください'",
        "occurredAt, 2026/11/01 11:30, '2026-11-01 11:30 の形（日本時間）で入力してください'",
        "occurredAt, 2026-02-30 09:00, '2026-11-01 11:30 の形（日本時間）で入力してください'",
        "kind, LOADING, 選択肢から選んでください",
        "sourceKind, EXTERNAL_RECORD, 選択肢から選んでください"
    })
    void 形式の誤りを項目の下とエラー要約に示し入力した値を残す(String field, String value, String message) throws Exception {
        mockMvc.perform(registration(field, value))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(message)));

        then(commandService).should(never()).registerMilestone(any());
    }

    @Test
    void 出典の参照は200文字まで受け付ける() throws Exception {
        given(commandService.registerMilestone(any()))
                .willReturn(new MilestoneRegistrationOutcome.Registered(1, TrackingStatus.PICKED_UP));

        mockMvc.perform(registration("sourceReference", "a".repeat(200))).andExpect(redirectedUrl(DETAIL));
    }

    @Test
    void 出典の参照が201文字なら誤りにする() throws Exception {
        mockMvc.perform(registration("sourceReference", "a".repeat(201)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("出典の参照は 200 文字までで入力してください")));

        then(commandService).should(never()).registerMilestone(any());
    }

    @Test
    void 出典の参照が空白だけなら誤りにする() throws Exception {
        mockMvc.perform(registration("sourceReference", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("出典の参照を入力してください")));

        then(commandService).should(never()).registerMilestone(any());
    }
}
