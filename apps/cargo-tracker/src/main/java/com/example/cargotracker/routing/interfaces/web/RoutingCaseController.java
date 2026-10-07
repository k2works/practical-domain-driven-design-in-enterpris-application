package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commandservices.CandidateCalculationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 経路設計案件一覧（S-05）と経路候補の比較（S-06）（US-06 AC1〜AC3。Bolt 17）。経路設計者だけが開ける（認可は
 * SecurityConfiguration）。画面と URL には案件番号だけを出す（D-4）。候補の算出・再算出は PRG で S-06 に戻る
 * （部分更新とライブリージョンは US-07 で S-06 に操作が増えるときに入れる。Bolt 17 計画の確認ポイント 12）。
 */
@Controller
@RequestMapping("/staff/routing-cases")
public class RoutingCaseController {

    private static final String LIST_VIEW = "routing/staff/routing-cases/list";
    private static final String SHOW_VIEW = "routing/staff/routing-cases/show";
    private static final String REDIRECT = "redirect:/staff/routing-cases/";
    private static final String RESULT = "result";
    private static final String PROBLEM = "problem";
    private static final String CONFLICT_MESSAGE = "ほかの経路設計者が先に候補を算出しました。最新の候補を確かめてください";

    private final RoutingCaseQueryService queryService;
    private final RoutingCaseCommandService commandService;

    public RoutingCaseController(RoutingCaseQueryService queryService, RoutingCaseCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** S-05。依頼の新しい順。 */
    @GetMapping
    public String list(Model model) {
        model.addAttribute(
                "cases",
                queryService.listCases().stream().map(RoutingCaseViews::row).toList());
        return LIST_VIEW;
    }

    /** S-06。算出の前は候補の算出の操作を、算出の後は候補と再算出の操作を示す。 */
    @GetMapping("/{number}")
    public String show(@PathVariable String number, Model model) {
        model.addAttribute(
                "routingCase",
                queryService
                        .findByNumber(parse(number))
                        .map(RoutingCaseViews::detail)
                        .orElseThrow(RoutingCaseController::notFound));
        return SHOW_VIEW;
    }

    /** 候補を算出・再算出する。結果の件数を S-06 の上部に示す。 */
    @PostMapping("/{number}/candidates")
    public String calculate(@PathVariable String number, RedirectAttributes redirectAttributes) {
        RoutingCaseNumber caseNumber = parse(number);
        return switch (commandService.calculateCandidates(new CalculateCandidatesCommand(caseNumber))) {
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

    private static String result(CandidateCalculation calculation) {
        String result = "候補を %d 件算出しました（適合 %d 件、除外 %d 件）"
                .formatted(calculation.kept(), calculation.conforming(), calculation.excluded());
        return calculation.omitted() > 0
                ? result + "。ほかに %d 件の候補があります（到着予定の遅いものを示していません）".formatted(calculation.omitted())
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
