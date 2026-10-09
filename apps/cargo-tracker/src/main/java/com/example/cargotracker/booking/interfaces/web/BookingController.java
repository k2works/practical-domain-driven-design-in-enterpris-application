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
import java.util.UUID;
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
 * S-09 本予約の確定と S-24 予約の詳細（営業担当者。US-04 AC1・AC2・AC4。Bolt 23b・24）。S-09 は画面そのものが確認の領域（BR-13。
 * C-17・S-07 と同じく別のダイアログは挟まない）。見積りは業務番号と見積り番号で指し、URL に内部の ID を出さない（D-4）。開いたときの
 * 判定は参考で、確定の可否は送ったときの照会（commit 時刻）で決まる（ADR-016）。S-09 は開くたびにコマンド ID を発行して隠し項目で送り、
 * 同じコマンド ID の再送には最初の結果を示す（B-INV-03）。隠し項目の欠け・形の誤りは利用者が入れる値ではないので 400 にする。
 */
@Controller
@RequestMapping("/staff/bookings")
public class BookingController {

    private static final String RECEPTION = "redirect:/staff/transport-requests";
    private static final String CONFIRMATION_VIEW = "booking/staff/bookings/new";
    private static final String DETAIL_VIEW = "booking/staff/bookings/show";
    private static final String PROBLEM = "problem";
    private static final String RESULT = "result";
    private static final String RESULT_LINK_HREF = "resultLinkHref";
    private static final String RESULT_LINK_LABEL = "resultLinkLabel";

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
        return showConfirmation(
                transportRequestNumber, quotationNo, CommandId.random(), List.of(), model, redirectAttributes);
    }

    /** 本予約を確定する。確定したら予約の詳細へリダイレクトし、追跡番号を示す（PRG）。 */
    @PostMapping
    public String confirm(
            @RequestParam("commandId") UUID commandId,
            @RequestParam("transportRequest") String transportRequestNumber,
            @RequestParam("quotation") int quotationNo,
            @RequestParam(name = "staffConfirmed", defaultValue = "false") boolean staffConfirmed,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        CommandId command = new CommandId(commandId);
        BookingConfirmationOutcome outcome = commandService.confirm(
                new ConfirmBookingCommand(command, transportRequestNumber, quotationNo, actor, staffConfirmed));
        String subject = BookingViews.subject(transportRequestNumber, quotationNo);
        return switch (outcome) {
            case BookingConfirmationOutcome.Confirmed(TrackingNumber trackingNumber) -> {
                redirectAttributes.addFlashAttribute(
                        RESULT, subject + " で本予約を確定しました。追跡番号は " + trackingNumber.value() + " です。追跡の開始を待っています。");
                yield "redirect:/staff/bookings/" + trackingNumber.value();
            }
            case BookingConfirmationOutcome.MissingConditions(List<BookingCondition> missing) ->
                showConfirmation(transportRequestNumber, quotationNo, command, missing, model, redirectAttributes);
            case BookingConfirmationOutcome.Expired _ ->
                unavailable(subject, QuotationUnavailability.EXPIRED, redirectAttributes);
            case BookingConfirmationOutcome.QuotationUnavailable(QuotationUnavailability reason) ->
                unavailable(subject, reason, redirectAttributes);
            case BookingConfirmationOutcome.AlreadyBooked(TrackingNumber trackingNumber) ->
                alreadyBooked(subject, trackingNumber, redirectAttributes);
            case BookingConfirmationOutcome.CommandConflict _ -> {
                // 正しい画面の操作では起きない（開くたびに新しいコマンド ID）。確定サービスが警告のログを残す
                redirectAttributes.addFlashAttribute(PROBLEM, "この操作はすでに別の内容で受け付けています。開き直してください。");
                yield RECEPTION;
            }
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
            CommandId commandId,
            List<BookingCondition> missing,
            Model model,
            RedirectAttributes redirectAttributes) {
        String subject = BookingViews.subject(transportRequestNumber, quotationNo);
        return switch (queryService.confirmation(transportRequestNumber, quotationNo)) {
            case BookingConfirmationPage.Available available -> {
                model.addAttribute(
                        "page", BookingViews.confirmation(transportRequestNumber, quotationNo, available, missing));
                model.addAttribute("commandId", commandId.value().toString());
                yield CONFIRMATION_VIEW;
            }
            case BookingConfirmationPage.Unavailable(QuotationUnavailability reason) ->
                unavailable(subject, reason, redirectAttributes);
            case BookingConfirmationPage.AlreadyBooked(TrackingNumber trackingNumber) ->
                alreadyBooked(subject, trackingNumber, redirectAttributes);
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

    /**
     * 同じ見積りの予約がある（別のコマンド ID・別の利用者の確定。B-INV-11）は、望んだ結果が成り立っているので警告でなく結果として示し、
     * 既存の予約への導線をお知らせの文の外に置く（Bolt 24。T-70）。
     */
    private static String alreadyBooked(
            String subject, TrackingNumber trackingNumber, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(RESULT, subject + " は既に予約に使われています（追跡番号 " + trackingNumber.value() + "）。");
        redirectAttributes.addFlashAttribute(RESULT_LINK_HREF, "/staff/bookings/" + trackingNumber.value());
        redirectAttributes.addFlashAttribute(RESULT_LINK_LABEL, "予約の詳細を開く");
        return RECEPTION;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
