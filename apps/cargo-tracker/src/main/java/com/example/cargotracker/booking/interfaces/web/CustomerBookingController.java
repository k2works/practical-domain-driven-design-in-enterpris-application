package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.queryservices.CustomerBookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.RecentBookings;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * C-06 予約一覧（荷主担当者。Bolt 27b）。ログインした荷主担当者の企業の本予約だけを一覧する。企業での絞り込みは照会のサービス
 * （リポジトリの照会）が担い、この画面は企業を比べない（BR-07。C-10 と同じ形）。行から C-10 の照会の結果と C-04 へは URL で
 * つなぎ、追跡・見積りの型を参照しない（BC の独立）。
 */
@Controller
@RequestMapping(CustomerBookingController.BASE_PATH)
public class CustomerBookingController {

    static final String BASE_PATH = "/customer/bookings";
    private static final String LIST_VIEW = "booking/bookings/list";

    private final CustomerBookingQueryService queryService;

    public CustomerBookingController(CustomerBookingQueryService queryService) {
        this.queryService = queryService;
    }

    /** 自社の本予約の一覧。確定時刻の新しい順に上限まで示し、上限を超えたかを示す。 */
    @GetMapping
    public String list(AuthenticatedActor actor, Model model) {
        RecentBookings recent = queryService.recent(actor.companyId());
        model.addAttribute("bookings", BookingViews.customerList(recent));
        model.addAttribute("truncated", recent.truncated());
        model.addAttribute("limit", recent.limit());
        return LIST_VIEW;
    }
}
