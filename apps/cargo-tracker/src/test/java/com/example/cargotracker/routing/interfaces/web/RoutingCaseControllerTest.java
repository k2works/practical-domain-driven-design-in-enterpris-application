package com.example.cargotracker.routing.interfaces.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commands.ConfirmRouteCommand;
import com.example.cargotracker.routing.application.internal.commandservices.CandidateCalculationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RouteConfirmationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteApprover;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** S-05 経路設計案件一覧・S-06 経路候補の比較・S-07 経路の確定（US-06 AC1〜AC3、US-07 AC1・AC2。Bolt 17・19）。 */
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
                        RouteVersionStatus.DRAFT,
                        at("2026-10-08T09:00:00Z"))));

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
    void 候補を算出した案件の状態は確定待ちと示す() throws Exception {
        when(queryService.listCases())
                .thenReturn(List.of(new RoutingCaseSummary(
                        NUMBER,
                        "TR-2026-0001",
                        1,
                        TOKYO,
                        ROTTERDAM,
                        DEADLINE,
                        at("2026-10-06T02:00:00Z"),
                        RouteVersionStatus.CANDIDATES_PRESENTED,
                        at("2026-10-08T09:00:00Z"))));

        mockMvc.perform(get("/staff/routing-cases"))
                .andExpect(content().string(Matchers.containsString("候補算出済み（確定待ち）")));
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
                // Bolt 17 レビュー D-61: 実際の接続時間と、規則がないことを判定できないと示す。経由港と期限までの余裕を示す
                .andExpect(content().string(Matchers.containsString("接続不足（SGSIN で接続 4 時間、必要 8 時間、SGSIN の規則）")))
                .andExpect(content().string(Matchers.containsString("接続を判定できない（HKHKG の接続時間規則が未登録。接続 1 日）")))
                .andExpect(content().string(Matchers.containsString("期限超過 1 日 12 時間")))
                .andExpect(content().string(Matchers.containsString("期限まで 2 日")))
                .andExpect(content().string(Matchers.containsString("SGSIN 積替え")))
                .andExpect(content().string(Matchers.containsString("直行")))
                .andExpect(content().string(Matchers.containsString("最も古い情報の取得時刻")))
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
                .andExpect(content().string(Matchers.containsString("条件に合う航海が見つかりませんでした")))
                .andExpect(content().string(Matchers.containsString("データ責任者")));
    }

    @Test
    void ない案件番号と形式の違う案件番号は404() throws Exception {
        when(queryService.findByNumber(any())).thenReturn(Optional.empty());

        mockMvc.perform(get(SHOW)).andExpect(status().isNotFound());
        mockMvc.perform(get("/staff/routing-cases/TR-2026-0001")).andExpect(status().isNotFound());
    }

    @Test
    void 候補を算出すると比較の画面に戻り件数を示す() throws Exception {
        when(commandService.calculateCandidates(new CalculateCandidatesCommand(NUMBER, 0, TestActors.STAFF_USER)))
                .thenReturn(new CandidateCalculationOutcome.Calculated(
                        NUMBER, new CandidateCalculation(22, 20, 18, at("2026-10-07T03:00:00Z"))));

        mockMvc.perform(post(SHOW + "/candidates").param("expectedVersion", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute(
                                "result",
                                "候補を 20 件算出しました（適合 18 件、除外 2 件）。ほかに 2 件の候補があります（適合を先に、到着予定の早い順に 20 件まで示しています）"));
    }

    @Test
    void ほかの経路設計者が先に算出していたら確かめるよう示す() throws Exception {
        when(commandService.calculateCandidates(new CalculateCandidatesCommand(NUMBER, 0, TestActors.STAFF_USER)))
                .thenReturn(new CandidateCalculationOutcome.Conflict());

        mockMvc.perform(post(SHOW + "/candidates").param("expectedVersion", "0"))
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute("problem", "ほかの経路設計者が先にこの案件を更新しました。最新の候補を確かめてください。"));
    }

    @Test
    void ない案件の候補は算出できず404() throws Exception {
        when(commandService.calculateCandidates(new CalculateCandidatesCommand(NUMBER, 0, TestActors.STAFF_USER)))
                .thenReturn(new CandidateCalculationOutcome.NotFound());

        mockMvc.perform(post(SHOW + "/candidates").param("expectedVersion", "0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 案件一覧は見積有効期限を示し期限を過ぎた案件は期限切れと示し確定済みの状態を示す() throws Exception {
        when(queryService.listCases())
                .thenReturn(List.of(
                        new RoutingCaseSummary(
                                NUMBER,
                                "TR-2026-0001",
                                1,
                                TOKYO,
                                ROTTERDAM,
                                DEADLINE,
                                at("2026-10-05T02:00:00Z"),
                                RouteVersionStatus.CONFIRMED,
                                at("2026-10-06T09:00:00Z")),
                        new RoutingCaseSummary(
                                new RoutingCaseNumber(2026, 89),
                                "TR-2026-0002",
                                1,
                                TOKYO,
                                ROTTERDAM,
                                DEADLINE,
                                at("2026-10-06T02:00:00Z"),
                                RouteVersionStatus.CANDIDATES_PRESENTED,
                                null)));

        mockMvc.perform(get("/staff/routing-cases"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("見積有効期限の近い順")))
                .andExpect(content().string(Matchers.containsString("見積有効期限")))
                .andExpect(content().string(Matchers.containsString("2026-10-06 18:00 Asia/Tokyo")))
                .andExpect(content().string(Matchers.containsString("見積りの期限切れ")))
                .andExpect(content().string(Matchers.containsString("確定済み")))
                .andExpect(content().string(Matchers.containsString("期限の記録なし")));
    }

    @Test
    void 比較の画面は適合の候補にだけ確定への入口を示し算出のフォームに版を持たせる() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(calculated()));

        mockMvc.perform(get(SHOW))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"" + SHOW + "/confirmation?candidate=1\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString(SHOW + "/confirmation?candidate=2"))))
                .andExpect(content().string(Matchers.containsString("name=\"expectedVersion\" value=\"0\"")));
    }

    @Test
    void 比較の画面は上限で示さなかった候補の数を常に示す() throws Exception {
        RoutingCase calculated = calculated();
        RouteVersion version = calculated.routeVersion();
        RoutingCase withOmitted = reconstitute(new RouteVersion(
                1,
                version.status(),
                version.candidates(),
                version.candidatesEvaluatedAt(),
                version.candidates().size() + 2,
                null));
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(withOmitted));

        mockMvc.perform(get(SHOW)).andExpect(content().string(Matchers.containsString("ほかに 2 件の候補があります")));
    }

    @Test
    void 確定した案件は確定した経路と根拠を示し算出と確定の操作を出さない() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(confirmed()));

        mockMvc.perform(get(SHOW))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("確定した経路")))
                .andExpect(content().string(Matchers.containsString("SGSIN の接続に 4 時間の余裕がある。")))
                .andExpect(content().string(Matchers.containsString("2026-10-07 14:00 Asia/Tokyo")))
                // 画面に内部の ID を出さない（D-4。Bolt 19 レビュー）
                .andExpect(content()
                        .string(Matchers.not(Matchers.containsString(
                                TestActors.STAFF_USER.value().toString()))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("候補を再算出"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("この候補で確定へ"))));
    }

    @Test
    void 確定の画面は選んだ候補と見積有効期限と判断根拠の入力と確定の操作を示す() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(calculated()));

        mockMvc.perform(get(SHOW + "/confirmation").param("candidate", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("経路の確定 RC-2026-0088")))
                .andExpect(content().string(Matchers.containsString("この経路で確定しますか")))
                .andExpect(content().string(Matchers.containsString("候補 1（SGSIN 積替え）")))
                .andExpect(content().string(Matchers.containsString("V-201")))
                .andExpect(content().string(Matchers.containsString("見積有効期限")))
                .andExpect(content().string(Matchers.containsString("2026-10-08 18:00 Asia/Tokyo")))
                .andExpect(content().string(Matchers.containsString("4,000 文字まで")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("maxlength"))))
                .andExpect(content().string(Matchers.containsString("担当営業に伝えてください")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("見積りに割り当てられ"))))
                .andExpect(content().string(Matchers.containsString("name=\"candidate\" value=\"1\"")))
                .andExpect(content().string(Matchers.containsString("name=\"expectedVersion\" value=\"0\"")))
                .andExpect(content().string(Matchers.containsString("この経路で確定する")));
    }

    @Test
    void 除外の候補の確定の画面は開かず比較の画面で理由を示す() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(calculated()));

        mockMvc.perform(get(SHOW + "/confirmation").param("candidate", "2"))
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute("problem", "除外の候補は確定できません。"));
    }

    @Test
    void ない候補とない案件の確定の画面は404() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(calculated()));

        mockMvc.perform(get(SHOW + "/confirmation").param("candidate", "99")).andExpect(status().isNotFound());
        mockMvc.perform(get("/staff/routing-cases/RC-2026-0001/confirmation").param("candidate", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 確定すると比較の画面に戻り確定したと示す() throws Exception {
        when(commandService.confirm(new ConfirmRouteCommand(NUMBER, 1, "根拠", 3, TestActors.STAFF_USER, true)))
                .thenReturn(new RouteConfirmationOutcome.Confirmed(NUMBER, 1, 1));

        mockMvc.perform(post(SHOW + "/confirmation")
                        .param("candidate", "1")
                        .param("rationale", "根拠")
                        .param("expectedVersion", "3"))
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute("result", "候補 1 の経路を確定しました。確定したことを担当営業に伝えてください。"));
    }

    @Test
    void 確定を拒否されたら理由をエラー要約に示し入力を残す() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(calculated()));
        when(commandService.confirm(new ConfirmRouteCommand(NUMBER, 1, "出発の後の根拠", 0, TestActors.STAFF_USER, true)))
                .thenReturn(new RouteConfirmationOutcome.Rejected(RouteConfirmationRejectionReason.ALREADY_DEPARTED));

        mockMvc.perform(post(SHOW + "/confirmation")
                        .param("candidate", "1")
                        .param("rationale", "出発の後の根拠")
                        .param("expectedVersion", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"error-summary\"")))
                .andExpect(content().string(Matchers.containsString("最初の区間の航海がすでに出発しているため確定できません")))
                .andExpect(content().string(Matchers.containsString(">出発の後の根拠</textarea>")));
    }

    @Test
    void 判断根拠がなければ入れるよう示す() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(calculated()));
        when(commandService.confirm(new ConfirmRouteCommand(NUMBER, 1, "", 0, TestActors.STAFF_USER, true)))
                .thenReturn(new RouteConfirmationOutcome.Rejected(RouteConfirmationRejectionReason.RATIONALE_MISSING));

        mockMvc.perform(post(SHOW + "/confirmation")
                        .param("candidate", "1")
                        .param("rationale", "")
                        .param("expectedVersion", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("判断根拠を入れてください。")))
                .andExpect(content().string(Matchers.containsString("href=\"#rationale\"")));
    }

    @Test
    void 画面を開いた後にほかの経路設計者が更新していたら確かめるよう示す() throws Exception {
        when(commandService.confirm(new ConfirmRouteCommand(NUMBER, 1, "根拠", 0, TestActors.STAFF_USER, true)))
                .thenReturn(new RouteConfirmationOutcome.Conflict());

        mockMvc.perform(post(SHOW + "/confirmation")
                        .param("candidate", "1")
                        .param("rationale", "根拠")
                        .param("expectedVersion", "0"))
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute("problem", "ほかの経路設計者が先にこの案件を更新しました。最新の候補を確かめてください。"));
    }

    @Test
    void 確定した案件と算出の前の案件の確定の画面は開かず比較の画面で理由を示す() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(confirmed()));

        mockMvc.perform(get(SHOW + "/confirmation").param("candidate", "1"))
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute("problem", "この案件の経路はすでに確定しているか、候補をまだ算出していません。"));

        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(open()));

        mockMvc.perform(get(SHOW + "/confirmation").param("candidate", "1"))
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute("problem", "この案件の経路はすでに確定しているか、候補をまだ算出していません。"));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} は比較の画面に戻して示す")
    @org.junit.jupiter.params.provider.EnumSource(
            value = RouteConfirmationRejectionReason.class,
            names = {"CANDIDATE_EXCLUDED", "NOT_CONFIRMABLE_STATE", "CANDIDATE_NOT_FOUND"})
    void 入力で直せない拒否は比較の画面に戻して理由を示す(RouteConfirmationRejectionReason reason) throws Exception {
        when(commandService.confirm(new ConfirmRouteCommand(NUMBER, 1, "根拠", 0, TestActors.STAFF_USER, true)))
                .thenReturn(new RouteConfirmationOutcome.Rejected(reason));

        mockMvc.perform(post(SHOW + "/confirmation")
                        .param("candidate", "1")
                        .param("rationale", "根拠")
                        .param("expectedVersion", "0"))
                .andExpect(redirectedUrl(SHOW))
                .andExpect(flash().attribute("problem", RoutingCaseController.message(reason)));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} は確定の画面で示す")
    @org.junit.jupiter.params.provider.EnumSource(
            value = RouteConfirmationRejectionReason.class,
            names = {"RATIONALE_TOO_LONG", "NOT_ROUTE_DESIGNER", "NO_LONGER_CONFORMING"})
    void 入力や再算出で直す拒否は確定の画面で示す(RouteConfirmationRejectionReason reason) throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(calculated()));
        when(commandService.confirm(new ConfirmRouteCommand(NUMBER, 1, "根拠", 0, TestActors.STAFF_USER, true)))
                .thenReturn(new RouteConfirmationOutcome.Rejected(reason));

        mockMvc.perform(post(SHOW + "/confirmation")
                        .param("candidate", "1")
                        .param("rationale", "根拠")
                        .param("expectedVersion", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(RoutingCaseController.message(reason))));
    }

    @Test
    void 確定の画面を描き直すときに候補がなくなっていたら比較の画面に戻す() throws Exception {
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(open()));
        when(commandService.confirm(new ConfirmRouteCommand(NUMBER, 1, "根拠", 0, TestActors.STAFF_USER, true)))
                .thenReturn(
                        new RouteConfirmationOutcome.Rejected(RouteConfirmationRejectionReason.NO_LONGER_CONFORMING));

        mockMvc.perform(post(SHOW + "/confirmation")
                        .param("candidate", "1")
                        .param("rationale", "根拠")
                        .param("expectedVersion", "0"))
                .andExpect(redirectedUrl(SHOW));
    }

    @Test
    void ない案件は確定できず404() throws Exception {
        when(commandService.confirm(new ConfirmRouteCommand(NUMBER, 1, "根拠", 0, TestActors.STAFF_USER, true)))
                .thenReturn(new RouteConfirmationOutcome.NotFound());

        mockMvc.perform(post(SHOW + "/confirmation")
                        .param("candidate", "1")
                        .param("rationale", "根拠")
                        .param("expectedVersion", "0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 見積りの期限切れの案件は比較と確定の画面で担当営業との相談を示す() throws Exception {
        when(queryService.findByNumber(NUMBER))
                .thenReturn(Optional.of(withExpiry(calculated(), "2026-10-07T00:00:00Z")));

        mockMvc.perform(get(SHOW))
                .andExpect(content().string(Matchers.containsString("見積りの期限切れ")))
                .andExpect(content().string(Matchers.containsString("担当営業と扱いを相談してください")));
        mockMvc.perform(get(SHOW + "/confirmation").param("candidate", "1"))
                .andExpect(content().string(Matchers.containsString("見積りの期限切れ")))
                .andExpect(content().string(Matchers.containsString("担当営業と扱いを相談してください")));
    }

    @Test
    void 期限の前の案件は期限切れと示さない() throws Exception {
        when(queryService.findByNumber(NUMBER))
                .thenReturn(Optional.of(withExpiry(calculated(), "2026-10-07T00:01:00Z")));

        mockMvc.perform(get(SHOW)).andExpect(content().string(Matchers.not(Matchers.containsString("見積りの期限切れ"))));
    }

    @Test
    void 判断根拠は表示でエスケープする() throws Exception {
        RoutingCase routingCase = calculated();
        routingCase.confirm(
                1,
                "<script>alert(1)</script>",
                new RouteApprover(TestActors.STAFF_USER.value(), true),
                List.of(new ConnectionRule(
                        UUID.randomUUID(), SINGAPORE, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null)),
                at("2026-10-07T05:00:00Z"),
                new ConstraintEvaluator());
        when(queryService.findByNumber(NUMBER)).thenReturn(Optional.of(routingCase));

        mockMvc.perform(get(SHOW))
                .andExpect(content().string(Matchers.containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("<script>alert(1)"))));
    }

    static RoutingCase withExpiry(RoutingCase source, String expiresAt) {
        return RoutingCase.reconstitute(
                source.id(),
                source.number(),
                source.transportRequestId(),
                source.transportRequestNumber(),
                source.transportRequestVersionNo(),
                source.quotationId(),
                source.routePolicyVia(),
                source.specification(),
                source.requestedAt(),
                at(expiresAt),
                source.requestedBy().orElse(null),
                source.routeVersions(),
                source.aggregateVersion());
    }

    static RoutingCase confirmed() {
        RoutingCase routingCase = calculated();
        routingCase.confirm(
                1,
                "SGSIN の接続に 4 時間の余裕がある。",
                new RouteApprover(TestActors.STAFF_USER.value(), true),
                List.of(new ConnectionRule(
                        UUID.randomUUID(), SINGAPORE, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null)),
                at("2026-10-07T05:00:00Z"),
                new ConstraintEvaluator());
        return routingCase;
    }

    static RoutingCase reconstitute(RouteVersion version) {
        RoutingCase source = open();
        return RoutingCase.reconstitute(
                source.id(),
                source.number(),
                source.transportRequestId(),
                source.transportRequestNumber(),
                source.transportRequestVersionNo(),
                source.quotationId(),
                source.routePolicyVia(),
                source.specification(),
                source.requestedAt(),
                source.quotationExpiresAt().orElse(null),
                source.requestedBy().orElse(null),
                List.of(version),
                0);
    }

    /** 画面の時計（S-05 の見積りの期限切れの判定）。 */
    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC);
        }
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
                at("2026-10-06T02:00:00Z"),
                at("2026-10-08T09:00:00Z"),
                UUID.randomUUID());
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
