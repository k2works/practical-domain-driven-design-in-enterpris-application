package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingConfirmationOutcome;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.internal.queryservices.BookingConfirmationPage;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.RecentBookings;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CommandId;
import java.util.List;
import java.util.Optional;
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
 * S-09 本予約の確定と S-24 予約の詳細（営業担当者。US-04 AC1〜AC5。Bolt 23b・24・35）。S-09 は画面そのものが確認の領域（BR-13。
 * C-17・S-07 と同じく別のダイアログは挟まない）。見積りは業務番号と見積り番号で指し、URL に内部の ID を出さない（D-4）。開いたときの
 * 判定は参考で、確定の可否は送ったときの照会（commit 時刻）で決まる（ADR-016）。S-09 は開くたびにコマンド ID を発行して隠し項目で送り、
 * 同じコマンド ID の再送には最初の結果を示す（B-INV-03）。隠し項目の欠け・形の誤りは利用者が入れる値ではないので 400 にする。
 * Bolt 38f2 から、どの画面も営業担当者の担当企業で絞る（US-16 AC4）。範囲の外は、いない予約・見積りと同じ 404 にし、確定の POST の
 * 範囲の外だけを試行として記録する（ADR-019。GET は記録しない。SEC-12）。
 */
@Controller
@RequestMapping("/staff/bookings")
public class BookingController {

    private static final String RECEPTION = "redirect:/staff/transport-requests";
    private static final String CONFIRMATION_VIEW = "booking/staff/bookings/new";
    private static final String DETAIL_VIEW = "booking/staff/bookings/show";
    private static final String LIST_VIEW = "booking/staff/bookings/list";
    private static final String PROBLEM = "problem";
    private static final String RESULT = "result";
    private static final String RESULT_LINK_HREF = "resultLinkHref";
    private static final String RESULT_LINK_LABEL = "resultLinkLabel";

    private final BookingCommandService commandService;
    private final BookingQueryService queryService;
    private final BookingOutOfScopeResponder outOfScope;

    public BookingController(
            BookingCommandService commandService,
            BookingQueryService queryService,
            BookingOutOfScopeResponder outOfScope) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.outOfScope = outOfScope;
    }

    /**
     * 予約一覧（S-10 の最小の表示。Bolt 25b）。確定した予約を確定時刻の新しい順に上限まで示し、追跡番号から S-24 を開ける。
     * 絞り込み・ページ送り・変更・取消申請の状況は US-05（W11）。Bolt 38f2 から担当企業の荷主の予約だけを示し、担当企業がなければ
     * 案内を示す。
     */
    @GetMapping
    public String list(AuthenticatedActor actor, Model model) {
        RecentBookings recent = queryService.recent(actor);
        model.addAttribute("noAssignedCompanies", actor.hasNoAssignedCompanies());
        model.addAttribute("bookings", BookingViews.list(recent));
        model.addAttribute("truncated", recent.truncated());
        model.addAttribute("limit", recent.limit());
        return LIST_VIEW;
    }

    /** 本予約の確定の画面（S-09）。確定に使えない見積りと確定済みは、受付一覧に戻して理由を示す。 */
    @GetMapping("/new")
    public String confirmation(
            @RequestParam("transportRequest") String transportRequestNumber,
            @RequestParam("quotation") int quotationNo,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        return showConfirmation(
                new Confirming(transportRequestNumber, quotationNo, CommandId.random(), actor),
                List.of(),
                false,
                model,
                redirectAttributes);
    }

    /**
     * 本予約を確定する。確定したら予約の詳細へリダイレクトし、追跡番号を示す（PRG）。確定のサービスの前に、見積りの行を押さえない照会で
     * 担当範囲を確かめ、範囲の外なら記録して 404 にする（Bolt 38f2。順序は役割 → 範囲 → 決着済み → 確定用の照会。範囲の外では予約済みも
     * 確定条件の欠けも示さない）。
     */
    @PostMapping
    public String confirm(
            @RequestParam("commandId") UUID commandId,
            @RequestParam("transportRequest") String transportRequestNumber,
            @RequestParam("quotation") int quotationNo,
            @RequestParam(name = "staffConfirmed", defaultValue = "false") boolean staffConfirmed,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        Optional<String> outOfScopeNumber = queryService.outOfScopeNumber(transportRequestNumber, quotationNo, actor);
        if (outOfScopeNumber.isPresent()) {
            throw outOfScope.recordAndNotFound(actor, outOfScopeNumber.get(), quotationNo);
        }
        CommandId command = new CommandId(commandId);
        BookingConfirmationOutcome outcome = commandService.confirm(
                new ConfirmBookingCommand(command, transportRequestNumber, quotationNo, actor, staffConfirmed));
        return respond(
                outcome,
                new Confirming(transportRequestNumber, quotationNo, command, actor),
                model,
                redirectAttributes);
    }

    /** 確定の結果ごとの応答（循環的複雑度を確定の前の確かめと分ける）。 */
    private String respond(
            BookingConfirmationOutcome outcome,
            Confirming confirming,
            Model model,
            RedirectAttributes redirectAttributes) {
        String subject = BookingViews.subject(confirming.transportRequestNumber(), confirming.quotationNo());
        return switch (outcome) {
            case BookingConfirmationOutcome.Confirmed(TrackingNumber trackingNumber) -> {
                redirectAttributes.addFlashAttribute(
                        RESULT, subject + " で本予約を確定しました。追跡番号は " + trackingNumber.value() + " です。");
                yield "redirect:/staff/bookings/" + trackingNumber.value();
            }
            case BookingConfirmationOutcome.MissingConditions(List<BookingCondition> missing) ->
                showConfirmation(confirming, missing, false, model, redirectAttributes);
            // 特殊貨物は S-09 を開いたときと同じ表示で示す。拒んだ結果を渡し、照会し直した結果だけに頼らない（US-04 AC5。Bolt 35）
            case BookingConfirmationOutcome.SpecialCargo _ ->
                showConfirmation(confirming, List.of(), true, model, redirectAttributes);
            case BookingConfirmationOutcome.Expired _ ->
                unavailable(subject, QuotationUnavailability.EXPIRED, redirectAttributes);
            case BookingConfirmationOutcome.QuotationUnavailable(QuotationUnavailability reason) ->
                unavailable(subject, reason, redirectAttributes);
            case BookingConfirmationOutcome.AlreadyBooked(TrackingNumber trackingNumber) ->
                alreadyBooked(subject, trackingNumber, redirectAttributes);
            case BookingConfirmationOutcome.CommandConflict _ -> {
                // 正しい画面の操作では起きない（開くたびに新しいコマンド ID）。確定サービスが警告のログを残す
                redirectAttributes.addFlashAttribute(
                        PROBLEM, subject + " は確定していません。この操作はすでに別の内容で受け付けています。受付一覧から開き直してください。");
                yield RECEPTION;
            }
            case BookingConfirmationOutcome.Forbidden _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            // 先の確かめの後に担当企業が変わったときだけ届く。記録の ID は見積りの公開 API が返す正規の業務番号（Bolt 38f2）
            case BookingConfirmationOutcome.OutOfScope(String canonicalNumber, int outOfScopeNo) ->
                throw outOfScope.recordAndNotFound(confirming.actor(), canonicalNumber, outOfScopeNo);
        };
    }

    /** 予約の詳細（S-24）。Bolt 23b の最小の表示。予約サガが処理中なら完了と示さない（ADR-015）。 */
    @GetMapping("/{trackingNumber}")
    public String detail(@PathVariable String trackingNumber, AuthenticatedActor actor, Model model) {
        TrackingNumber parsed =
                BookingViews.parseTrackingNumber(trackingNumber).orElseThrow(BookingController::notFound);
        model.addAttribute(
                "booking",
                BookingViews.detail(queryService.detail(parsed, actor).orElseThrow(BookingController::notFound)));
        return DETAIL_VIEW;
    }

    /** 本予約の確定の画面を示す対象（業務番号・見積り番号・送るコマンド ID・操作者）。 */
    private record Confirming(
            String transportRequestNumber, int quotationNo, CommandId commandId, AuthenticatedActor actor) {}

    private String showConfirmation(
            Confirming confirming,
            List<BookingCondition> missing,
            boolean rejectedAsSpecialCargo,
            Model model,
            RedirectAttributes redirectAttributes) {
        String number = confirming.transportRequestNumber();
        int quotationNo = confirming.quotationNo();
        String subject = BookingViews.subject(number, quotationNo);
        return switch (queryService.confirmation(number, quotationNo, confirming.actor())) {
            case BookingConfirmationPage.Available available -> {
                model.addAttribute(
                        "page",
                        BookingViews.confirmation(number, quotationNo, available, missing, rejectedAsSpecialCargo));
                model.addAttribute("commandId", confirming.commandId().value().toString());
                yield CONFIRMATION_VIEW;
            }
            case BookingConfirmationPage.Unavailable(QuotationUnavailability reason) ->
                unavailable(subject, reason, redirectAttributes);
            case BookingConfirmationPage.AlreadyBooked(TrackingNumber trackingNumber) ->
                alreadyBooked(subject, trackingNumber, redirectAttributes);
            // 範囲の外は、いない見積りと同じ 404（記録しない。予約済みでも追跡番号を示さない。Bolt 38f2）
            case BookingConfirmationPage.OutOfScope _ -> throw notFound();
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
                    case ROUTE_REDESIGN_REQUIRED -> subject + " は割り当てた経路が再設計要のため本予約を確定できません。経路設計者と扱いを相談してください。";
                    case ROUTE_ALREADY_DEPARTED -> subject + " は割り当てた経路の最初の区間が出発済みのため本予約を確定できません。荷主と扱いを相談してください。";
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
        redirectAttributes.addFlashAttribute(RESULT, subject + " はすでに予約に使われています（追跡番号 " + trackingNumber.value() + "）。");
        redirectAttributes.addFlashAttribute(RESULT_LINK_HREF, "/staff/bookings/" + trackingNumber.value());
        // リンクの名前だけで何の予約か分かるように、追跡番号を入れる（WCAG 2.4.4。Bolt 24 レビュー U-1）
        redirectAttributes.addFlashAttribute(RESULT_LINK_LABEL, trackingNumber.value() + " の予約の詳細を開く");
        return RECEPTION;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
