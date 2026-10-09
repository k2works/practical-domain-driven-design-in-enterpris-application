package com.example.cargotracker.tracking.interfaces.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** S-11 追跡一覧と S-12 追跡の詳細（追跡管理者。Bolt 26）。 */
@Controller
@RequestMapping("/staff/tracking-records")
public class TrackingRecordController {

    private static final String LIST_VIEW = "tracking/staff/tracking-records/list";

    /** 追跡一覧（S-11 の最小の表示。Bolt 26）。 */
    @GetMapping
    public String list() {
        return LIST_VIEW;
    }
}
