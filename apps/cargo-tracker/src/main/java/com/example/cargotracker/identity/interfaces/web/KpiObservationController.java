package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commands.RegisterKpiBaselineCommand;
import com.example.cargotracker.identity.application.internal.commandservices.KpiBaselineCommandService;
import com.example.cargotracker.identity.application.internal.commandservices.KpiBaselineOutcome;
import com.example.cargotracker.identity.application.internal.queryservices.KpiComparisonQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.KpiWeeklyQueryService;
import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.time.Clock;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 社内業務 Web の KPI の照会（S-22。監査担当者。W9 の Bolt 44-1 から週次の KPI-01・KPI-02 を示す）と、見積依頼ごとの KPI 計測記録の一覧。
 * 未提示の行に、一覧を開いた時刻での提出からの経過時間を示す（Bolt 21 の開発レビュー D-82）。
 */
@Controller
public class KpiObservationController {

    /** 週次の KPI に示す週の数（今週を含む。W9 の Bolt 44-1）。 */
    private static final int WEEKS = 12;

    private static final String LIST_VIEW = "identity/kpi-observations/list";
    private static final String LIST_PATH = "/staff/kpi-observations";
    private static final String PROBLEM = "problem";

    private final KpiObservationQueryService queryService;
    private final KpiWeeklyQueryService weeklyQueryService;
    private final KpiComparisonQueryService comparisonQueryService;
    private final KpiBaselineCommandService baselineCommandService;
    private final Clock clock;

    public KpiObservationController(
            KpiObservationQueryService queryService,
            KpiWeeklyQueryService weeklyQueryService,
            KpiComparisonQueryService comparisonQueryService,
            KpiBaselineCommandService baselineCommandService,
            Clock clock) {
        this.queryService = queryService;
        this.weeklyQueryService = weeklyQueryService;
        this.comparisonQueryService = comparisonQueryService;
        this.baselineCommandService = baselineCommandService;
        this.clock = clock;
    }

    @GetMapping(LIST_PATH)
    public String list(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", KpiBaselineForm.empty());
        }
        return listView(model);
    }

    /**
     * KPI 基準値を登録する（US-21 AC5。W9 の Bolt 44-2）。登録できるのは監査担当者だけ（URL の規則とサービスの両方で止める）。登録したら
     * 一覧へ戻して結果を示す（PRG）。入力の誤りは、同じ画面に理由を示して入力を残す。日付と数値の変換の誤りは、入力が空のまま届くので案内する。
     */
    @PostMapping(LIST_PATH + "/baselines")
    public String registerBaseline(
            @ModelAttribute("form") KpiBaselineForm form,
            BindingResult binding,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors() || isIncomplete(form)) {
            // 変換できなかった項目は空のまま画面に戻す（変換できた項目は残す）
            model.addAttribute("form", form == null ? KpiBaselineForm.empty() : form);
            model.addAttribute(PROBLEM, "日付と数値の形式を確かめてください（日付は 2026-10-05 の形、値は数字）");
            return listView(model);
        }
        KpiKind kpi = kpiOf(form.kpi());
        if (kpi == null) {
            model.addAttribute(PROBLEM, "KPI の種類を選んでください");
            return listView(model);
        }
        return switch (baselineCommandService.register(actor, commandOf(kpi, form))) {
            case KpiBaselineOutcome.Registered _ -> {
                redirect.addFlashAttribute("result", kpi.code() + " の基準値を登録しました");
                yield "redirect:" + LIST_PATH;
            }
            case KpiBaselineOutcome.InvalidInput(String message) -> {
                model.addAttribute(PROBLEM, message);
                yield listView(model);
            }
            case KpiBaselineOutcome.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        };
    }

    /** 変換できなかった項目（日付・数値）があるか。 */
    private static boolean isIncomplete(KpiBaselineForm form) {
        return form == null || form.periodFrom() == null || form.periodTo() == null || form.valueP50() == null;
    }

    private static RegisterKpiBaselineCommand commandOf(KpiKind kpi, KpiBaselineForm form) {
        return new RegisterKpiBaselineCommand(
                kpi,
                form.periodFrom(),
                form.periodTo(),
                form.valueP50(),
                Optional.ofNullable(form.valueP90()),
                form.method() == null ? "" : form.method());
    }

    private static KpiKind kpiOf(String code) {
        try {
            return code == null ? null : KpiKind.ofCode(code);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private String listView(Model model) {
        model.addAttribute(
                "comparison", KpiComparisonView.from(comparisonQueryService.compare(clock.instant(), WEEKS)));
        model.addAttribute(
                "weeks",
                weeklyQueryService.recentWeeks(clock.instant(), WEEKS).stream()
                        .map(KpiWeeklyView::from)
                        .toList());
        model.addAttribute(
                "observations",
                queryService.findAll().stream()
                        .map(observation -> KpiObservationView.from(observation, clock.instant()))
                        .toList());
        return LIST_VIEW;
    }
}
