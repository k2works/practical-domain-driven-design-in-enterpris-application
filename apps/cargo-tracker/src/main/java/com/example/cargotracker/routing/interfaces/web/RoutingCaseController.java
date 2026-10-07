package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 経路設計案件一覧（S-05）と経路候補の比較（S-06）。骨組み（ステップ 4 の Red）。
 */
@Controller
@RequestMapping("/staff/routing-cases")
public class RoutingCaseController {

    private final RoutingCaseQueryService queryService;
    private final RoutingCaseCommandService commandService;

    public RoutingCaseController(RoutingCaseQueryService queryService, RoutingCaseCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }
}
