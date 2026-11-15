package com.example.cargotracker.identity.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.application.internal.commandservices.KpiBaselineCommandService;
import com.example.cargotracker.identity.application.internal.commandservices.KpiBaselineOutcome;
import com.example.cargotracker.identity.application.internal.queryservices.KpiComparison;
import com.example.cargotracker.identity.application.internal.queryservices.KpiComparisonQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.KpiPilotSummary;
import com.example.cargotracker.identity.application.internal.queryservices.KpiWeeklyQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.KpiWeeklyRow;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.AUDITOR)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = KpiObservationController.class)
@Import(KpiObservationControllerTest.FixedClock.class)
class KpiObservationControllerTest {

    /** 一覧を開いた時刻（未提示の行の、提出からの経過時間の基準）。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-06T06:00:59Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    KpiObservationQueryService queryService;

    @MockitoBean
    KpiWeeklyQueryService weeklyQueryService;

    @MockitoBean
    KpiComparisonQueryService comparisonQueryService;

    @MockitoBean
    KpiBaselineCommandService baselineCommandService;

    private static final KpiPilotSummary NO_PILOT =
            new KpiPilotSummary(0, Optional.empty(), Optional.empty(), 0, 0, Optional.empty());

    /** 基準値の比較は、どのテストでも空の値を返す（個別のテストで上書きする）。 */
    @org.junit.jupiter.api.BeforeEach
    void emptyComparison() {
        given(comparisonQueryService.compare(any(), anyInt()))
                .willReturn(new KpiComparison(
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), NO_PILOT, 0));
    }

    // 基準値とパイロット中の値の比較、未完了の発行の件数（US-21 AC5。W9 の Bolt 44-2）

    @Test
    void 基準値とパイロット中の値を並べ_差と算出方法と登録者と未完了の発行の件数を示す() throws Exception {
        KpiBaseline kpi01 = KpiBaseline.register(
                KpiKind.KPI_01,
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 11, 1),
                new BigDecimal("480"),
                Optional.of(new BigDecimal("1440")),
                "現行の受付台帳から算出",
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-11-16T01:00:00Z")),
                Optional.empty());
        KpiBaseline kpi02 = KpiBaseline.register(
                KpiKind.KPI_02,
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 11, 1),
                new BigDecimal("30"),
                Optional.empty(),
                "電話・メールの記録から算出",
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-11-16T02:00:00Z")),
                Optional.empty());
        given(comparisonQueryService.compare(any(), anyInt()))
                .willReturn(new KpiComparison(
                        Optional.of(kpi01),
                        Optional.of("監査 花子"),
                        Optional.of(kpi02),
                        Optional.of("監査 花子"),
                        new KpiPilotSummary(
                                5,
                                Optional.of(Duration.ofMinutes(240)),
                                Optional.of(Duration.ofMinutes(720)),
                                20,
                                11,
                                Optional.of(55.0)),
                        3));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("基準値との比較")))
                .andExpect(content().string(containsString("8 時間 0 分")))
                .andExpect(content().string(containsString("4 時間 0 分")))
                .andExpect(content().string(containsString("50.0% 短縮")))
                .andExpect(content().string(containsString("+25.0 ポイント")))
                .andExpect(content().string(containsString("現行の受付台帳から算出")))
                .andExpect(content().string(containsString("2026-10-05 〜 2026-11-01")))
                .andExpect(content().string(containsString("監査 花子")))
                .andExpect(content().string(containsString("未完了のイベントの発行")))
                .andExpect(content().string(containsString(">3<")));
    }

    @Test
    void 基準値が未登録なら未登録と示し差を出さない() throws Exception {
        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("未登録")))
                .andExpect(content().string(containsString("基準値を登録する")));
    }

    @Test
    void 基準値を登録すると_登録した旨を示して一覧へ戻る() throws Exception {
        given(baselineCommandService.register(any(), any()))
                .willReturn(new KpiBaselineOutcome.Registered(KpiBaseline.register(
                        KpiKind.KPI_01,
                        LocalDate.of(2026, 10, 5),
                        LocalDate.of(2026, 11, 1),
                        new BigDecimal("480"),
                        Optional.empty(),
                        "算出方法",
                        new UserId(UUID.randomUUID()),
                        new UtcInstant(Instant.parse("2026-11-16T01:00:00Z")),
                        Optional.empty())));

        mockMvc.perform(post("/staff/kpi-observations/baselines")
                        .param("kpi", "KPI-01")
                        .param("periodFrom", "2026-10-05")
                        .param("periodTo", "2026-11-01")
                        .param("valueP50", "480")
                        .param("method", "算出方法"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/kpi-observations"))
                .andExpect(flash().attribute("result", "KPI-01 の基準値を登録しました"));
    }

    @Test
    void 入力の誤りは同じ画面に理由を示し入力を残す() throws Exception {
        given(baselineCommandService.register(any(), any()))
                .willReturn(new KpiBaselineOutcome.InvalidInput("基準値の期間は開始日から終了日まで 28 日（パイロット開始前の 4 週間）です"));

        mockMvc.perform(post("/staff/kpi-observations/baselines")
                        .param("kpi", "KPI-01")
                        .param("periodFrom", "2026-10-05")
                        .param("periodTo", "2026-10-20")
                        .param("valueP50", "480")
                        .param("method", "現行の受付台帳から算出"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("28 日（パイロット開始前の 4 週間）です")))
                .andExpect(content().string(containsString("現行の受付台帳から算出")))
                .andExpect(content().string(containsString("2026-10-20")));
    }

    @Test
    void 日付や数値の形が正しくない入力は変換できず案内する() throws Exception {
        mockMvc.perform(post("/staff/kpi-observations/baselines")
                        .param("kpi", "KPI-01")
                        .param("periodFrom", "10/05")
                        .param("periodTo", "2026-11-01")
                        .param("valueP50", "あ")
                        .param("method", "算出方法"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("日付と数値の形式を確かめてください")));
        verify(baselineCommandService, never()).register(any(), any());
    }

    @Test
    void 監査担当者でないときは登録できない旨を示す() throws Exception {
        given(baselineCommandService.register(any(), any())).willReturn(new KpiBaselineOutcome.NotPermitted());

        mockMvc.perform(post("/staff/kpi-observations/baselines")
                        .param("kpi", "KPI-01")
                        .param("periodFrom", "2026-10-05")
                        .param("periodTo", "2026-11-01")
                        .param("valueP50", "480")
                        .param("method", "算出方法"))
                .andExpect(status().isForbidden());
    }

    // 週次の KPI（US-21 AC4。W9 の Bolt 44-1）

    @Test
    void 週次の値を週の範囲とKPI01の件数と中央値とP90とKPI02の総数と割合で示す() throws Exception {
        given(weeklyQueryService.recentWeeks(Instant.parse("2026-10-06T06:00:59Z"), 12))
                .willReturn(List.of(
                        new KpiWeeklyRow(
                                Instant.parse("2026-10-05T00:00:00Z"),
                                3,
                                Optional.of(Duration.ofHours(3)),
                                Optional.of(Duration.ofMinutes(500)),
                                8,
                                6,
                                Optional.of(75.0)),
                        new KpiWeeklyRow(
                                Instant.parse("2026-09-28T00:00:00Z"),
                                0,
                                Optional.empty(),
                                Optional.empty(),
                                0,
                                0,
                                Optional.empty())));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("週次の KPI")))
                .andExpect(content().string(containsString("2026-10-05 〜 2026-10-11（UTC）")))
                .andExpect(content().string(containsString("3 時間 0 分")))
                .andExpect(content().string(containsString("8 時間 20 分")))
                .andExpect(content().string(containsString("75.0%")))
                .andExpect(content().string(containsString("2026-09-28 〜 2026-10-04（UTC）")))
                .andExpect(content().string(containsString("算出しない")));
    }

    @Test
    void KPI計測記録の一覧に業務番号と提出時刻を日時表示の共通部品で表示し内部のIDを出さない() throws Exception {
        UUID transportRequestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        given(queryService.findAll())
                .willReturn(List.of(KpiObservation.recordSubmission(
                        transportRequestId,
                        "TR-2026-0001",
                        new CompanyId(UUID.randomUUID()),
                        new UtcInstant(Instant.parse("2026-10-05T13:04:05Z")))));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TR-2026-0001")))
                .andExpect(content().string(not(containsString(transportRequestId.toString()))))
                .andExpect(content()
                        .string(containsString("2026-10-05 22:04 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 13:04）")));
    }

    @Test
    void 業務番号のない古い記録は業務番号なしと表示する() throws Exception {
        given(queryService.findAll())
                .willReturn(List.of(KpiObservation.recordSubmission(
                        UUID.randomUUID(),
                        null,
                        new CompanyId(UUID.randomUUID()),
                        new UtcInstant(Instant.parse("2026-10-05T13:04:05Z")))));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("（業務番号なし）")));
    }

    @Test
    void 表の見出しは画面の語の見積依頼ごとと示し実装の語の輸送要求を出さない() throws Exception {
        given(queryService.findAll())
                .willReturn(List.of(KpiObservation.recordSubmission(
                        UUID.randomUUID(),
                        "TR-2026-0001",
                        new CompanyId(UUID.randomUUID()),
                        new UtcInstant(Instant.parse("2026-10-05T13:04:05Z")))));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<caption>見積依頼ごとの KPI-01 の時刻とリードタイム</caption>")))
                .andExpect(content().string(containsString("aria-label=\"見積依頼ごとの KPI-01 の時刻とリードタイム\"")))
                .andExpect(content().string(not(containsString("輸送要求ごと"))));
    }

    @Test
    void 記録がなければその旨を表示する() throws Exception {
        given(queryService.findAll()).willReturn(List.of());

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("まだ記録はありません。")));
    }

    @Test
    void 提示済みの記録は最初の提示時刻とKPI01リードタイムを時間と分で表示する() throws Exception {
        KpiObservation observation = KpiObservation.recordSubmission(
                UUID.randomUUID(),
                "TR-2026-0001",
                new CompanyId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));
        observation.recordPresentation(new UtcInstant(Instant.parse("2026-10-06T03:30:59Z")));
        given(queryService.findAll()).willReturn(List.of(observation));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(containsString("2026-10-06 12:30 Asia/Tokyo（UTC+09:00）（UTC 2026-10-06 03:30）")))
                .andExpect(content().string(containsString("26 時間 30 分")));
    }

    @Test
    void 未提示の記録は未提示と示しリードタイムを算出しない() throws Exception {
        given(queryService.findAll())
                .willReturn(List.of(KpiObservation.recordSubmission(
                        UUID.randomUUID(),
                        "TR-2026-0001",
                        new CompanyId(UUID.randomUUID()),
                        new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")))));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("未提示（提出から 29 時間 0 分）")))
                .andExpect(content().string(containsString("算出しない")))
                .andExpect(content().string(containsString("「—」は、まだ提示していないため算出していないことを示します。")));
    }
}
