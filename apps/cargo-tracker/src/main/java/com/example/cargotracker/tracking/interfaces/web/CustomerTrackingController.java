package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerTrackingQueryService;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerTrackingView;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * C-10 追跡の照会（荷主担当者。US-09 AC1、BR-07。Bolt 27）。ログインした荷主担当者の企業の追跡記録だけを照会する。企業での絞り込みは
 * 照会のサービス（リポジトリの照会）が担い、この画面は企業を比べない。他社・存在しない・形式の誤りの追跡番号は、どれも同じ 404 にして
 * 追跡記録の存在を漏らさない。
 */
@Controller
@RequestMapping(CustomerTrackingController.BASE_PATH)
public class CustomerTrackingController {

    static final String BASE_PATH = "/customer/tracking-records";
    private static final String LIST_VIEW = "tracking/tracking-records/list";
    private static final String DETAIL_VIEW = "tracking/tracking-records/show";
    private static final String NOT_FOUND_VIEW = "tracking/tracking-records/not-found";
    private static final String FORMAT_MESSAGE = "CT で始まる 14 文字で入力してください。英字の I・L・O と数字の 0・1 は使いません（例: CTABCDEFGH2345）";
    /** 区切りとして入れられる空白とハイフン（電話やメールで区切って伝えられた番号。Bolt 27 の開発レビューの判断）。 */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s-]");

    private static final String TRACKING_NUMBER = "trackingNumber";

    private final CustomerTrackingQueryService queryService;

    public CustomerTrackingController(CustomerTrackingQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 自社の追跡記録の一覧と追跡番号の入力。追跡番号を付けて開くと、形式が正しければ照会の結果へ移し（1 画面 = 1 URL）、誤りがあれば
     * エラー要約と項目の下に示して入力値を残す。空白とハイフンは除き、英字は大文字にそろえる。
     */
    @GetMapping
    public String list(
            @ModelAttribute("trackingNumberForm") TrackingNumberForm form,
            BindingResult bindingResult,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (form.getTrackingNumber() != null) {
            String text =
                    SEPARATORS.matcher(form.getTrackingNumber()).replaceAll("").toUpperCase(Locale.ROOT);
            if (text.isEmpty()) {
                bindingResult.rejectValue(
                        TRACKING_NUMBER, "trackingNumber.required", MilestoneFormConverter.REQUIRED_MESSAGE);
            } else {
                Optional<TrackingNumber> parsed = TrackingRecordViews.parseTrackingNumber(text);
                if (parsed.isPresent()) {
                    redirectAttributes.addAttribute(
                            TRACKING_NUMBER, parsed.get().value());
                    return "redirect:" + BASE_PATH + "/{trackingNumber}";
                }
                bindingResult.rejectValue(TRACKING_NUMBER, "trackingNumber.format", FORMAT_MESSAGE);
            }
        }
        RecentTrackingRecords recent = queryService.recent(actor.companyId());
        model.addAttribute("trackingRecords", TrackingRecordViews.customerList(recent));
        model.addAttribute("truncated", recent.truncated());
        model.addAttribute("limit", recent.limit());
        return LIST_VIEW;
    }

    /**
     * 照会の結果。自社の追跡記録でなければ、存在しない・形式の誤りの追跡番号と同じ 404 にする。打ち間違いが日常の経路なので、既定の
     * エラーページではなく、どれにも同じ C-10 の案内と戻り方を示す（Bolt 27 の開発レビューの判断）。
     */
    @GetMapping("/{trackingNumber}")
    public String detail(
            @PathVariable String trackingNumber, AuthenticatedActor actor, Model model, HttpServletResponse response) {
        Optional<CustomerTrackingView> found = TrackingRecordViews.parseTrackingNumber(trackingNumber)
                .flatMap(number -> queryService.find(number, actor.companyId()));
        if (found.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return NOT_FOUND_VIEW;
        }
        model.addAttribute("trackingRecord", TrackingRecordViews.customerDetail(found.get()));
        return DETAIL_VIEW;
    }
}
