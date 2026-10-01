package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 社内業務 Web の KPI 計測記録の一覧（S-22 KPI の照会の前身となる仮の画面）。
 */
@Controller
public class KpiObservationController {

    private final KpiObservationQueryService queryService;

    public KpiObservationController(KpiObservationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/staff/kpi-observations")
    public String list(Model model) {
        model.addAttribute(
                "observations",
                queryService.findAll().stream().map(KpiObservationView::from).toList());
        return "identity/kpi-observations/list";
    }
}
