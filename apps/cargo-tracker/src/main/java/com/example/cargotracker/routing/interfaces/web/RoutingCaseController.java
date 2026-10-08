package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commands.ConfirmRouteCommand;
import com.example.cargotracker.routing.application.internal.commandservices.CandidateCalculationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RouteConfirmationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 経路設計案件一覧（S-05）、経路候補の比較（S-06）、経路の確定（S-07）（US-06 AC1〜AC3、US-07 AC1・AC2。Bolt 17・19）。
 * 経路設計者だけが開ける（認可は SecurityConfiguration）。画面と URL には案件番号だけを出す（D-4）。候補の算出・再算出と確定は
 * PRG で S-06 に戻る。算出と確定のフォームは案件の版を持ち、違えば競合として最新を確かめるよう示す（Bolt 17 レビュー D-64）。
 * S-07 は画面そのものが確認の領域（BR-13。見積りへの回答 C-05 と同じ形で、別の確認のダイアログは挟まない）。
 */
@Controller
@RequestMapping("/staff/routing-cases")
public class RoutingCaseController {

    private static final String LIST_VIEW = "routing/staff/routing-cases/list";
    private static final String SHOW_VIEW = "routing/staff/routing-cases/show";
    private static final String REDIRECT = "redirect:/staff/routing-cases/";
    private static final String RESULT = "result";
    private static final String PROBLEM = "problem";
    private static final String CONFIRMATION_VIEW = "routing/staff/routing-cases/confirmation";
    private static final String CONFLICT_MESSAGE = "ほかの経路設計者が先にこの案件を更新しました。最新の候補を確かめてください。";

    /** S-07 にとどまって示す拒否の理由（入力を直せば、または再算出の判断に使う）。ほかは S-06 に戻して示す。 */
    private static final Set<RouteConfirmationRejectionReason> SHOWN_ON_CONFIRMATION = EnumSet.of(
            RouteConfirmationRejectionReason.RATIONALE_MISSING,
            RouteConfirmationRejectionReason.RATIONALE_TOO_LONG,
            RouteConfirmationRejectionReason.NOT_ROUTE_DESIGNER,
            RouteConfirmationRejectionReason.ALREADY_DEPARTED,
            RouteConfirmationRejectionReason.NO_LONGER_CONFORMING);

    private static final Set<RouteConfirmationRejectionReason> RATIONALE_REASONS = EnumSet.of(
            RouteConfirmationRejectionReason.RATIONALE_MISSING, RouteConfirmationRejectionReason.RATIONALE_TOO_LONG);

    private final RoutingCaseQueryService queryService;
    private final RoutingCaseCommandService commandService;
    private final Clock clock;

    public RoutingCaseController(
            RoutingCaseQueryService queryService, RoutingCaseCommandService commandService, Clock clock) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.clock = clock;
    }

    /** S-05。見積有効期限の近い順（Bolt 19）。期限を過ぎた案件は期限切れと示す。 */
    @GetMapping
    public String list(Model model) {
        Instant now = clock.instant();
        model.addAttribute(
                "cases",
                queryService.listCases().stream()
                        .map(summary -> RoutingCaseViews.row(summary, now))
                        .toList());
        return LIST_VIEW;
    }

    /** S-06。算出の前は候補の算出の操作を、算出の後は候補と再算出の操作を示す。 */
    @GetMapping("/{number}")
    public String show(@PathVariable String number, Model model) {
        model.addAttribute(
                "routingCase",
                queryService
                        .findByNumber(parse(number))
                        .map(routingCase -> RoutingCaseViews.detail(routingCase, clock.instant()))
                        .orElseThrow(RoutingCaseController::notFound));
        return SHOW_VIEW;
    }

    /** 候補を算出・再算出する。結果の件数を S-06 の上部に示す。 */
    @PostMapping("/{number}/candidates")
    public String calculate(
            @PathVariable String number,
            @RequestParam long expectedVersion,
            AuthenticatedActor actor,
            RedirectAttributes redirectAttributes) {
        RoutingCaseNumber caseNumber = parse(number);
        return switch (commandService.calculateCandidates(
                new CalculateCandidatesCommand(caseNumber, expectedVersion, actor.userId()))) {
            case CandidateCalculationOutcome.Calculated(
                    RoutingCaseNumber calculated,
                    CandidateCalculation calculation) -> {
                redirectAttributes.addFlashAttribute(RESULT, result(calculation));
                yield REDIRECT + calculated.text();
            }
            case CandidateCalculationOutcome.Conflict() -> {
                redirectAttributes.addFlashAttribute(PROBLEM, CONFLICT_MESSAGE);
                yield REDIRECT + caseNumber.text();
            }
            case CandidateCalculationOutcome.NotFound() -> throw notFound();
        };
    }

    /** S-07。適合の候補だけを開ける。除外の候補・確定できない状態は S-06 に戻して理由を示す。 */
    @GetMapping("/{number}/confirmation")
    public String confirmation(
            @PathVariable String number,
            @RequestParam int candidate,
            Model model,
            RedirectAttributes redirectAttributes) {
        RoutingCase routingCase = find(parse(number));
        if (!routingCase.confirmable()) {
            redirectAttributes.addFlashAttribute(
                    PROBLEM, message(RouteConfirmationRejectionReason.NOT_CONFIRMABLE_STATE));
            return REDIRECT + routingCase.number().text();
        }
        RouteCandidate selected = candidateOf(routingCase, candidate).orElseThrow(RoutingCaseController::notFound);
        if (!selected.evaluation().conforming()) {
            redirectAttributes.addFlashAttribute(PROBLEM, message(RouteConfirmationRejectionReason.CANDIDATE_EXCLUDED));
            return REDIRECT + routingCase.number().text();
        }
        model.addAttribute("page", RoutingCaseViews.confirmation(routingCase, selected, clock.instant()));
        model.addAttribute("rationale", "");
        return CONFIRMATION_VIEW;
    }

    /** 判断根拠を記録して経路を確定する（US-07 AC1・AC2）。拒否は S-07 に理由をエラー要約で示し入力を残す。 */
    @PostMapping("/{number}/confirmation")
    public String confirm(
            @PathVariable String number,
            @RequestParam int candidate,
            @RequestParam(defaultValue = "") String rationale,
            @RequestParam long expectedVersion,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        RoutingCaseNumber caseNumber = parse(number);
        RouteConfirmationOutcome outcome = commandService.confirm(new ConfirmRouteCommand(
                caseNumber, candidate, rationale, expectedVersion, actor.userId(), actor.hasRole(Role.ROUTE_DESIGNER)));
        return switch (outcome) {
            case RouteConfirmationOutcome.Confirmed confirmed -> {
                redirectAttributes.addFlashAttribute(
                        RESULT, "候補 %d の経路を確定しました。確定した経路は見積りに割り当てられ、荷主の承認に進みます。".formatted(confirmed.candidateNo()));
                yield REDIRECT + confirmed.number().text();
            }
            case RouteConfirmationOutcome.Conflict() -> {
                redirectAttributes.addFlashAttribute(PROBLEM, CONFLICT_MESSAGE);
                yield REDIRECT + caseNumber.text();
            }
            case RouteConfirmationOutcome.NotFound() -> throw notFound();
            case RouteConfirmationOutcome.Rejected(RouteConfirmationRejectionReason reason) -> {
                if (!SHOWN_ON_CONFIRMATION.contains(reason)) {
                    redirectAttributes.addFlashAttribute(PROBLEM, message(reason));
                    yield REDIRECT + caseNumber.text();
                }
                RoutingCase routingCase = find(caseNumber);
                Optional<RouteCandidate> selected = candidateOf(routingCase, candidate);
                if (selected.isEmpty()) {
                    // 拒否の後に候補が算出し直されていた。S-06 で最新の候補を確かめてもらう
                    redirectAttributes.addFlashAttribute(
                            PROBLEM, message(RouteConfirmationRejectionReason.CANDIDATE_NOT_FOUND));
                    yield REDIRECT + caseNumber.text();
                }
                model.addAttribute("page", RoutingCaseViews.confirmation(routingCase, selected.get(), clock.instant()));
                model.addAttribute("rationale", rationale);
                model.addAttribute("error", message(reason));
                model.addAttribute("rationaleError", RATIONALE_REASONS.contains(reason));
                yield CONFIRMATION_VIEW;
            }
        };
    }

    /** 確定の拒否の理由の文言（UI 設計 S-07）。 */
    static String message(RouteConfirmationRejectionReason reason) {
        return switch (reason) {
            case RATIONALE_MISSING -> "判断根拠を入れてください。";
            case RATIONALE_TOO_LONG -> "判断根拠は %,d 文字までで入れてください。".formatted(DecisionRationale.MAX_LENGTH);
            case NOT_ROUTE_DESIGNER -> "経路を確定できるのは経路設計者だけです。";
            case NOT_CONFIRMABLE_STATE -> "この案件の経路はすでに確定しているか、候補をまだ算出していません。";
            case CANDIDATE_NOT_FOUND -> "選んだ候補がありません。経路候補の比較で最新の候補を確かめてください。";
            case CANDIDATE_EXCLUDED -> "除外の候補は確定できません。";
            case ALREADY_DEPARTED -> "最初の区間の航海がすでに出発しているため確定できません。候補を再算出してください。";
            case NO_LONGER_CONFORMING -> "いまの接続時間規則で判定し直すと条件を満たさないため確定できません。候補を再算出してください。";
        };
    }

    private RoutingCase find(RoutingCaseNumber number) {
        return queryService.findByNumber(number).orElseThrow(RoutingCaseController::notFound);
    }

    private static Optional<RouteCandidate> candidateOf(RoutingCase routingCase, int candidateNo) {
        return routingCase.routeVersion().candidates().stream()
                .filter(each -> each.candidateNo() == candidateNo)
                .findFirst();
    }

    private static String result(CandidateCalculation calculation) {
        String result = "候補を %d 件算出しました（適合 %d 件、除外 %d 件）"
                .formatted(calculation.kept(), calculation.conforming(), calculation.excluded());
        return calculation.omitted() > 0
                ? result + "。ほかに %d 件の候補があります（適合を先に、到着予定の早い順に 20 件まで示しています）".formatted(calculation.omitted())
                : result;
    }

    /** 案件番号の形でなければ、ない案件と同じに扱う（404）。 */
    private static RoutingCaseNumber parse(String number) {
        try {
            return RoutingCaseNumber.parse(number);
        } catch (IllegalArgumentException _) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
