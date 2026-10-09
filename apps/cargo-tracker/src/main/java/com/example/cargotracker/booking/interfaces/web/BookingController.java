package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingConfirmationOutcome;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.internal.queryservices.BookingConfirmationPage;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CommandId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-09 本予約の確定と S-24 予約の詳細（営業担当者。US-04 AC1・AC2。Bolt 23b）。S-09 は画面そのものが確認の領域（BR-13。C-17・S-07 と
 * 同じく別のダイアログは挟まない）。見積りは業務番号と見積り番号で指し、URL に内部の ID を出さない（D-4）。開いたときの判定は参考で、
 * 確定の可否は送ったときの照会（commit 時刻）で決まる（ADR-016）。
 */
@Controller
@RequestMapping("/staff/bookings")
public class BookingController {

    private static final String RECEPTION = "redirect:/staff/transport-requests";
    private static final String CONFIRMATION_VIEW = "booking/staff/bookings/new";
    private static final String DETAIL_VIEW = "booking/staff/bookings/show";
    private static final String PROBLEM = "problem";
    private static final String RESULT = "result";

    private final BookingCommandService commandService;
    private final BookingQueryService queryService;

    public BookingController(BookingCommandService commandService, BookingQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /** 本予約の確定の画面（S-09）。確定に使えない見積りと確定済みは、受付一覧に戻して理由を示す。 */
    @GetMapping("/new")
    public String confirmation(
            @RequestParam("transportRequest") String transportRequestNumber,
            @RequestParam("quotation") int quotationNo,
            Model model,
            RedirectAttributes redirectAttributes) {
        return showConfirmation(transportRequestNumber, quotationNo, List.of(), model, redirectAttributes);
    }

    /** 本予約を確定する。確定したら予約の詳細へリダイレクトし、追跡番号を示す（PRG）。 */
    @PostMapping
    public String confirm(
            @RequestParam("transportRequest") String transportRequestNumber,
            @RequestParam("quotation") int quotationNo,
            @RequestParam(name = "staffConfirmed", defaultValue = "false") boolean staffConfirmed,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        BookingConfirmationOutcome outcome = commandService.confirm(new ConfirmBookingCommand(
                CommandId.random(), transportRequestNumber, quotationNo, actor, staffConfirmed));
        String subject = BookingViews.subject(transportRequestNumber, quotationNo);
        return switch (outcome) {
            case BookingConfirmationOutcome.Confirmed(TrackingNumber trackingNumber) -> {
                redirectAttributes.addFlashAttribute(
                        RESULT, subject + " で本予約を確定しました。追跡番号は " + trackingNumber.value() + " です。追跡の開始を待っています。");
                yield "redirect:/staff/bookings/" + trackingNumber.value();
            }
            case BookingConfirmationOutcome.MissingConditions(List<BookingCondition> missing) ->
                showConfirmation(transportRequestNumber, quotationNo, missing, model, redirectAttributes);
            case BookingConfirmationOutcome.Expired _ ->
                unavailable(subject, QuotationUnavailability.EXPIRED, redirectAttributes);
            case BookingConfirmationOutcome.QuotationUnavailable(QuotationUnavailability reason) ->
                unavailable(subject, reason, redirectAttributes);
            case BookingConfirmationOutcome.AlreadyBooked _ -> alreadyBooked(subject, redirectAttributes);
            case BookingConfirmationOutcome.CommandConflict _ -> throw new ResponseStatusException(HttpStatus.CONFLICT);
            case BookingConfirmationOutcome.Forbidden _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        };
    }

    /** 予約の詳細（S-24）。Bolt 23b の最小の表示。予約サガが処理中なら完了と示さない（ADR-015）。 */
    @GetMapping("/{trackingNumber}")
    public String detail(@PathVariable String trackingNumber, Model model) {
        TrackingNumber parsed =
                BookingViews.parseTrackingNumber(trackingNumber).orElseThrow(BookingController::notFound);
        model.addAttribute(
                "booking", BookingViews.detail(queryService.detail(parsed).orElseThrow(BookingController::notFound)));
        return DETAIL_VIEW;
    }

    private String showConfirmation(
            String transportRequestNumber,
            int quotationNo,
            List<BookingCondition> missing,
            Model model,
            RedirectAttributes redirectAttributes) {
        String subject = BookingViews.subject(transportRequestNumber, quotationNo);
        return switch (queryService.confirmation(transportRequestNumber, quotationNo)) {
            case BookingConfirmationPage.Available available -> {
                model.addAttribute(
                        "page", BookingViews.confirmation(transportRequestNumber, quotationNo, available, missing));
                yield CONFIRMATION_VIEW;
            }
            case BookingConfirmationPage.Unavailable(QuotationUnavailability reason) ->
                unavailable(subject, reason, redirectAttributes);
            case BookingConfirmationPage.AlreadyBooked _ -> alreadyBooked(subject, redirectAttributes);
        };
    }

    /**
     * 確定に使えない見積りを受付一覧に戻して理由を示す。見つからない見積り（業務番号の形でない番号を含む。形の判定は見積りの公開 API に
     * 任せ、予約に業務番号の形の写しを持たない）は 404（ほかの画面と同じ）。
     */
    private static String unavailable(
            String subject, QuotationUnavailability reason, RedirectAttributes redirectAttributes) {
        String message =
                switch (reason) {
                    case EXPIRED -> subject + " は有効期限を過ぎたため本予約を確定できません。再見積りが必要です。";
                    case REPLACED -> subject + " は新しい見積りに置き換えられたため本予約を確定できません。最新の見積りを確かめてください。";
                    case NOT_APPROVED -> subject + " は荷主の承認がまだのため本予約を確定できません。";
                    case NOT_FOUND -> throw notFound();
                };
        redirectAttributes.addFlashAttribute(PROBLEM, message);
        return RECEPTION;
    }

    /** 確定済み（二重送信など）は、望んだ結果が成り立っているので警告でなく結果として示す。既存の追跡番号を示すのは AC4 の Bolt 24。 */
    private static String alreadyBooked(String subject, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(RESULT, subject + " はすでに本予約を確定しています。");
        return RECEPTION;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
