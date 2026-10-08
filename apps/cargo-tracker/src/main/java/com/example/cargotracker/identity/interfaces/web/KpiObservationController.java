package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import java.time.Clock;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 社内業務 Web の KPI 計測記録の一覧（S-22 KPI の照会の前身となる仮の画面）。
 * 未提示の行に、一覧を開いた時刻での提出からの経過時間を示す（Bolt 21 の開発レビュー D-82）。
 */
@Controller
public class KpiObservationController {

    private final KpiObservationQueryService queryService;
    private final Clock clock;

    public KpiObservationController(KpiObservationQueryService queryService, Clock clock) {
        this.queryService = queryService;
        this.clock = clock;
    }

    @GetMapping("/staff/kpi-observations")
    public String list(Model model) {
        model.addAttribute(
                "observations",
                queryService.findAll().stream()
                        .map(observation -> KpiObservationView.from(observation, clock.instant()))
                        .toList());
        return "identity/kpi-observations/list";
    }
}
