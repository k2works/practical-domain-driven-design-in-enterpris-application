package com.example.cargotracker.routing.interfaces.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commandservices.CandidateCalculationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** S-05 経路設計案件一覧・S-06 経路候補の比較（US-06 AC1〜AC3。Bolt 17）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.ROUTE_DESIGNER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = RoutingCaseController.class)
class RoutingCaseControllerTest {

    static final RoutingCaseNumber NUMBER = new RoutingCaseNumber(2026, 88);
    static final String SHOW = "/staff/routing-cases/RC-2026-0088";
    static final Location TOKYO = new Location("JPTYO");
    static final Location SINGAPORE = new Location("SGSIN");
    static final Location HONG_KONG = new Location("HKHKG");
    static final Location ROTTERDAM = new Location("NLRTM");
    static final UtcInstant DEADLINE = at("2026-11-02T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RoutingCaseQueryService queryService;

    @MockitoBean
    RoutingCaseCommandService commandService;

    @Test
    void 案件一覧は案件番号と見積依頼と条件と依頼の日時と状態を示し案件を開ける() throws Exception {
        when(queryService.listCases())
                .thenReturn(List.of(new RoutingCaseSummary(
                        NUMBER,
                        "TR-2026-0001",
                        1,
                        TOKYO,
                        ROTTERDAM,
                        DEADLINE,
                        at("2026-10-06T02:00:00Z"),
                        RouteVersionStatus.DRAFT)));

        mockMvc.perform(get("/staff/routing-cases"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("経路設計案件一覧")))
                .andExpect(content().string(Matchers.containsString("href=\"" + SHOW + "\"")))
                .andExpect(content().string(Matchers.containsString("TR-2026-0001 版 1")))
                .andExpect(content().string(Matchers.containsString("JPTYO → NLRTM")))
                .andExpect(content()
                        .string(Matchers.containsString(
                                "2026-11-02 09:00 Asia/Tokyo（UTC+09:00）（UTC 2026-11-02 00:00）")))
                .andExpect(content().string(Matchers.containsString("2026-10-06 11:00 Asia/Tokyo")))
                .andExpect(content().string(Matchers.containsString("候補の算出待ち")));
    }

    @Test
    void 案件がなければその旨を示す() throws Exception {
        when(queryService.listCases()).thenReturn(List.of());

        mockMvc.perform(get("/staff/routing-cases"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("詳細経路設計の依頼はまだありません")));
    }

    @Test
    void 算出の前の案件は条件と経路方針と候補の算出の操作を示す() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(open()));

        mockMvc.perform(get(SHOW))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("経路候補の比較 RC-2026-0088")))
                .andExpect(content().string(Matchers.containsString("JPTYO → NLRTM")))
                .andExpect(content().string(Matchers.containsString("SGSIN")))
                .andExpect(content().string(Matchers.containsString("まだ候補を算出していません")))
                .andExpect(content().string(Matchers.containsString("action=\"" + SHOW + "/candidates\"")))
                .andExpect(content().string(Matchers.containsString("候補を算出")));
    }

    @Test
    void 算出した候補は適合と除外と理由と接続余裕と情報の取得時刻を示す() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(calculated()));

        mockMvc.perform(get(SHOW))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("候補 1")))
                .andExpect(content().string(Matchers.containsString("適合")))
                .andExpect(content().string(Matchers.containsString("除外")))
                .andExpect(content().string(Matchers.containsString("V-201")))
                .andExpect(content().string(Matchers.containsString("余裕 4 時間")))
                .andExpect(content().string(Matchers.containsString("不足 4 時間")))
                .andExpect(content().string(Matchers.containsString("接続不足（必要 8 時間、SGSIN の規則）")))
                .andExpect(content().string(Matchers.containsString("接続できない（HKHKG の接続時間規則がない）")))
                .andExpect(content().string(Matchers.containsString("期限超過 1 日 12 時間")))
                .andExpect(content().string(Matchers.containsString("参照情報版 V-LATE@1")))
                .andExpect(content().string(Matchers.containsString("2026-10-01 15:10 Asia/Tokyo")))
                .andExpect(content().string(Matchers.containsString("候補を再算出")));
    }

    @Test
    void 算出したが候補がなければ航海の情報を確かめるよう示す() throws Exception {
        RoutingCase routingCase = open();
        routingCase.calculateCandidates(
                List.of(),
                List.of(),
                at("2026-10-07T03:00:00Z"),
                new RouteCandidateFinder(),
                new ConstraintEvaluator());
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(routingCase));

        mockMvc.perform(get(SHOW))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("条件に合う航海が見つかりませんでした")));
    }

    @Test
    void ない案件番号と形式の違う案件番号は404() throws Exception {
        when(queryService.findByNumber(any())).thenReturn(Optional.empty());

        mockMvc.perform(get(SHOW)).andExpect(status().isNotFound());
        mockMvc.perform(get("/staff/routing-cases/TR-2026-0001")).andExpect(status().isNotFound());
    }

    @Test
    void 候補を算出すると比較の画面に戻り件数を示す() throws Exception {
        when(commandService.calculateCandidates(new CalculateCandidatesCommand(NUMBER)))
                .thenReturn(new CandidateCalculationOutcome.Calculated(
                        NUMBER, new CandidateCalculation(22, 20, 18, at("2026-10-07T03:00:00Z"))));

        mockMvc.perform(post(SHOW + "/candidates"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute(
                                "result", "候補を 20 件算出しました（適合 18 件、除外 2 件）。ほかに 2 件の候補があります（到着予定の遅いものを示していません）"));
    }

    @Test
    void ほかの経路設計者が先に算出していたら確かめるよう示す() throws Exception {
        when(commandService.calculateCandidates(new CalculateCandidatesCommand(NUMBER)))
                .thenReturn(new CandidateCalculationOutcome.Conflict());

        mockMvc.perform(post(SHOW + "/candidates"))
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute("problem", "ほかの経路設計者が先に候補を算出しました。最新の候補を確かめてください"));
    }

    @Test
    void ない案件の候補は算出できず404() throws Exception {
        when(commandService.calculateCandidates(new CalculateCandidatesCommand(NUMBER)))
                .thenReturn(new CandidateCalculationOutcome.NotFound());

        mockMvc.perform(post(SHOW + "/candidates")).andExpect(status().isNotFound());
    }

    static RoutingCase open() {
        return RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                NUMBER,
                UUID.randomUUID(),
                "TR-2026-0001",
                1,
                UUID.randomUUID(),
                List.of(SINGAPORE),
                new RouteSpecification(TOKYO, ROTTERDAM, DEADLINE, "GENERAL"),
                at("2026-10-06T02:00:00Z"));
    }

    /** 適合（接続 12 時間）、接続不足（4 時間）、規則のない香港、期限超過 1 日 12 時間。 */
    static RoutingCase calculated() {
        RoutingCase routingCase = open();
        routingCase.calculateCandidates(
                List.of(
                        voyage(
                                "V-201",
                                call(TOKYO, null, "2026-10-10T00:00:00Z"),
                                call(SINGAPORE, "2026-10-20T00:00:00Z", null)),
                        voyage(
                                "V-301",
                                call(SINGAPORE, null, "2026-10-20T12:00:00Z"),
                                call(ROTTERDAM, "2026-10-31T00:00:00Z", null)),
                        voyage(
                                "V-302",
                                call(SINGAPORE, null, "2026-10-20T04:00:00Z"),
                                call(ROTTERDAM, "2026-10-30T00:00:00Z", null)),
                        voyage(
                                "V-202",
                                call(TOKYO, null, "2026-10-11T00:00:00Z"),
                                call(HONG_KONG, "2026-10-15T00:00:00Z", null)),
                        voyage(
                                "V-304",
                                call(HONG_KONG, null, "2026-10-16T00:00:00Z"),
                                call(ROTTERDAM, "2026-10-29T00:00:00Z", null)),
                        voyage(
                                "V-LATE",
                                call(TOKYO, null, "2026-10-12T00:00:00Z"),
                                call(ROTTERDAM, "2026-11-03T12:00:00Z", null))),
                List.of(new ConnectionRule(
                        UUID.randomUUID(), SINGAPORE, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null)),
                at("2026-10-07T03:00:00Z"),
                new RouteCandidateFinder(),
                new ConstraintEvaluator());
        return routingCase;
    }

    static Voyage voyage(String number, PortCall... calls) {
        return new Voyage(number, List.of(calls), number + "@1", at("2026-10-01T06:10:00Z"));
    }

    static PortCall call(Location port, String arrival, String departure) {
        return new PortCall(port, arrival == null ? null : at(arrival), departure == null ? null : at(departure));
    }

    static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}
