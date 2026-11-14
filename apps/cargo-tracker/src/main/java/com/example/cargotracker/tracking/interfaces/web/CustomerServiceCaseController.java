package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerServiceCaseItem;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerServiceCaseList;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseQueryService;
import com.example.cargotracker.tracking.domain.model.entities.CaseAnswer;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * C-14 問い合わせ一覧（荷主担当者。US-23 AC1・AC4。Bolt 42-2）。自社の問い合わせ（有人案件）を、起票の新しい順に、受付番号・状態・次回更新日時・
 * 最新の回答で示す。企業での絞り込みは照会のサービスが担い、この画面は企業を比べない。URL の規則（{@code /customer/**} は荷主担当者だけ）が
 * ほかの役割を止める。詳細（C-15）と再開は 42-3。
 */
@Controller
@RequestMapping(CustomerServiceCaseController.BASE_PATH)
public class CustomerServiceCaseController {

    static final String BASE_PATH = "/customer/inquiries";
    private static final String LIST_VIEW = "tracking/customer/service-cases/list";

    private final ServiceCaseQueryService queryService;

    public CustomerServiceCaseController(ServiceCaseQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public String list(AuthenticatedActor actor, Model model) {
        CustomerServiceCaseList list = queryService.customerList(actor);
        model.addAttribute("serviceCases", rows(list.items()));
        model.addAttribute("truncated", list.truncated());
        model.addAttribute("limit", list.limit());
        // 問い合わせの窓口の受付時間（既定の営業カレンダー。平日 9:00〜18:00。台帳 D-164。仮）を、荷主の日時表示と同じ時間帯で示す（AC4）
        model.addAttribute("receptionHours", "平日 09:00〜18:00 " + DateTimeDisplay.customerZoneLabel(Instant.now()));
        return LIST_VIEW;
    }

    /** 一覧の 1 行（画面に出す文字）。 */
    record Row(String number, String status, String nextUpdateAt, String latestAnswer) {}

    private static List<Row> rows(List<CustomerServiceCaseItem> items) {
        return items.stream()
                .map(item -> new Row(
                        item.number().value(),
                        ServiceCaseViews.status(item.status()),
                        DateTimeDisplay.customer(item.nextUpdateAt().instant()),
                        item.latestAnswer().map(CaseAnswer::content).orElse("回答はまだありません。")))
                .toList();
    }
}
