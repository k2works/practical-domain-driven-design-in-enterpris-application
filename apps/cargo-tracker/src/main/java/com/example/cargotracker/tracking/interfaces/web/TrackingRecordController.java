package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * S-11 追跡一覧と S-12 追跡の詳細（追跡管理者。Bolt 26）。追跡管理者のホームは S-11。主要実績の登録（S-13）は Bolt 26b、絞り込み
 * （確認中・鮮度超過・訂正承認待ち）は W7 以後。
 */
@Controller
@RequestMapping("/staff/tracking-records")
public class TrackingRecordController {

    private static final String LIST_VIEW = "tracking/staff/tracking-records/list";
    private static final String DETAIL_VIEW = "tracking/staff/tracking-records/show";

    private final TrackingRecordQueryService queryService;

    public TrackingRecordController(TrackingRecordQueryService queryService) {
        this.queryService = queryService;
    }

    /** 追跡一覧（S-11 の最小の表示）。追跡記録を追跡の開始時刻の新しい順に上限まで示し、追跡番号から S-12 を開ける。 */
    @GetMapping
    public String list(Model model) {
        RecentTrackingRecords recent = queryService.recent();
        model.addAttribute("trackingRecords", TrackingRecordViews.list(recent));
        model.addAttribute("truncated", recent.truncated());
        model.addAttribute("limit", recent.limit());
        return LIST_VIEW;
    }

    /** 追跡の詳細（S-12 の最小の表示）。形式の誤った追跡番号は、ない追跡番号と同じく 404 にする。 */
    @GetMapping("/{trackingNumber}")
    public String detail(@PathVariable String trackingNumber, Model model) {
        model.addAttribute(
                "trackingRecord",
                TrackingRecordViews.parseTrackingNumber(trackingNumber)
                        .flatMap(queryService::detail)
                        .map(TrackingRecordViews::detail)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return DETAIL_VIEW;
    }
}
