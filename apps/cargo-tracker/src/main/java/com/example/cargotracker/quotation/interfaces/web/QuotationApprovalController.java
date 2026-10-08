package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.ApproveQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.ShipperApprovalOutcome;
import com.example.cargotracker.quotation.application.internal.queryservices.QuotationQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * C-17 見積りと経路の承認（US-24 AC4・AC5。Bolt 20）。荷主担当者が料金・有効期限と確定した経路を確かめて承認する。
 * 画面そのものが確認の領域（BR-13。C-05 と同じく別のダイアログは挟まない）。承認は本予約の確定ではない（担当営業が確定する。US-04）。
 * 荷主企業と承認者は、ログインした荷主担当者（認証の主体 {@link AuthenticatedActor}。ADR-012）のもの。
 */
@Controller
@RequestMapping("/customer/transport-requests/{number}/quotations/{quotationNo}/approval")
public class QuotationApprovalController {

    private static final String BASE_PATH = "/customer/transport-requests";
    private static final String APPROVAL_VIEW = "quotation/transport-requests/approval";
    private static final String PROBLEM = "problem";
    private static final String RESULT = "result";

    private final QuotationResponseService responseService;
    private final QuotationQueryService quotationQueryService;
    private final Clock clock;

    public QuotationApprovalController(
            QuotationResponseService responseService, QuotationQueryService quotationQueryService, Clock clock) {
        this.responseService = responseService;
        this.quotationQueryService = quotationQueryService;
        this.clock = clock;
    }

    /**
     * 承認の画面。荷主に提示した見積りだけを開ける（荷主企業で絞り、ほかは 404）。承認できない見積り（割当ての前・失効・置換済み・
     * 承認済み）は、見積依頼の詳細に戻して理由を示す。承認できるかは集約に問い合わせる。
     */
    @GetMapping
    public String show(
            @PathVariable String number,
            @PathVariable int quotationNo,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber parsed = parse(number);
        Quotation quotation = quotationQueryService.findVisible(parsed, actor.companyId()).stream()
                .filter(candidate -> candidate.quotationNo() == quotationNo)
                .findFirst()
                .orElseThrow(QuotationApprovalController::notFound);
        UtcInstant now = new UtcInstant(clock.instant());
        Optional<QuotationRejection> rejection = quotation.approvalRejectionAt(now);
        if (rejection.isPresent()) {
            return rejected(parsed, quotationNo, rejection.get(), redirectAttributes);
        }
        model.addAttribute("number", parsed.text());
        model.addAttribute("quotationLabel", QuotationViews.label(parsed, quotationNo));
        model.addAttribute(
                "quotation",
                QuotationViews.view(
                        quotation, TransportRequestLabels::customerDateTime, now, QuotationViews.Audience.CUSTOMER));
        return APPROVAL_VIEW;
    }

    /** 承認する。承認したら見積依頼の詳細へリダイレクトし、結果を示す（PRG）。 */
    @PostMapping
    public String approve(
            @PathVariable String number,
            @PathVariable int quotationNo,
            AuthenticatedActor actor,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber parsed = parse(number);
        ShipperApprovalOutcome outcome = responseService.approve(
                new ApproveQuotationCommand(parsed, quotationNo, actor.companyId(), actor.userId()));
        return switch (outcome) {
            case ShipperApprovalOutcome.Approved(TransportRequestNumber approvedNumber, int approvedNo) ->
                redirectToDetail(
                        parsed,
                        RESULT,
                        QuotationViews.label(approvedNumber, approvedNo) + " の見積りと経路を承認しました。担当営業が本予約を確定します。",
                        redirectAttributes);
            case ShipperApprovalOutcome.Rejected(QuotationRejection reason) ->
                rejected(parsed, quotationNo, reason, redirectAttributes);
            case ShipperApprovalOutcome.Conflict _ ->
                redirectToDetail(parsed, PROBLEM, "見積りが更新されました。最新の見積りと経路を確かめてから承認してください", redirectAttributes);
            case ShipperApprovalOutcome.NotFound _ -> throw notFound();
        };
    }

    /**
     * 承認を受け付けなかったときに見積依頼の詳細へ戻す。承認済み（二重送信など）は、荷主の望んだ結果が成り立っているので
     * 警告でなく結果として示す（回答と同じ。Bolt 12 レビュー R-13）。荷主には社内の言葉（置換済み）を使わない。
     */
    private static String rejected(
            TransportRequestNumber number,
            int quotationNo,
            QuotationRejection reason,
            RedirectAttributes redirectAttributes) {
        String subject = QuotationViews.label(number, quotationNo);
        if (reason == QuotationRejection.ALREADY_APPROVED) {
            return redirectToDetail(number, RESULT, subject + " の見積りと経路はすでに承認しています", redirectAttributes);
        }
        String message =
                switch (reason) {
                    case EXPIRED -> subject + " は有効期限を過ぎたため承認できません。新しい見積りは担当営業にご依頼ください";
                    case REPLACED -> subject + " は新しい見積りに置き換えられたため承認できません。見積依頼の詳細で最新の状況をご確認ください";
                    default -> subject + " はまだ承認できる見積りではありません。経路が確定したら承認できます";
                };
        return redirectToDetail(number, PROBLEM, message, redirectAttributes);
    }

    private static String redirectToDetail(
            TransportRequestNumber number, String attribute, String message, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(attribute, message);
        return "redirect:" + BASE_PATH + "/" + number.text();
    }

    private static TransportRequestNumber parse(String number) {
        return TransportRequestViews.parseNumber(number).orElseThrow(QuotationApprovalController::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
