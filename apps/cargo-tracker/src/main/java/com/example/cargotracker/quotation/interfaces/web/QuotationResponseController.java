package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteDesignRequestOutcome;
import com.example.cargotracker.quotation.application.internal.queryservices.QuotationQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
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
 * C-05 見積りへの回答（US-24 AC1。Bolt 12）。荷主担当者が見積りと経路方針を確かめ、詳細経路設計へ進む。辞退・相談（AC2・AC3）は
 * 後の Bolt で、それまでは担当営業への連絡を案内する。確認の領域は挟まない（C-05 自体が確かめる画面。2026-10-06 の決定）。
 * 荷主企業と回答者はいまは仮の主体のもの。認証（US-18）を入れたら、認証の主体に差し替える。
 */
@Controller
@RequestMapping("/customer/transport-requests/{number}/quotations/{quotationNo}/response")
public class QuotationResponseController {

    private static final String BASE_PATH = "/customer/transport-requests";
    private static final String RESPONSE_VIEW = "quotation/transport-requests/response";
    private static final String PROBLEM = "problem";

    private final QuotationResponseService responseService;
    private final QuotationQueryService quotationQueryService;
    private final ProvisionalActorProperties provisionalActor;
    private final Clock clock;

    public QuotationResponseController(
            QuotationResponseService responseService,
            QuotationQueryService quotationQueryService,
            ProvisionalActorProperties provisionalActor,
            Clock clock) {
        this.responseService = responseService;
        this.quotationQueryService = quotationQueryService;
        this.provisionalActor = provisionalActor;
        this.clock = clock;
    }

    /**
     * 回答の画面。荷主に提示した見積りだけを開ける（荷主企業で絞り、ほかは 404）。回答できない見積り（失効・置換済み・回答済み）は、
     * 見積依頼の詳細に戻して理由を示す。回答できるかは集約に問い合わせる。
     */
    @GetMapping
    public String show(
            @PathVariable String number,
            @PathVariable int quotationNo,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber parsed = parse(number);
        Quotation quotation = quotationQueryService.findVisible(parsed, shipper()).stream()
                .filter(candidate -> candidate.quotationNo() == quotationNo)
                .findFirst()
                .orElseThrow(QuotationResponseController::notFound);
        UtcInstant now = new UtcInstant(clock.instant());
        Optional<QuotationRejection> rejection = quotation.responseRejectionAt(now);
        if (rejection.isPresent()) {
            return rejected(parsed, quotationNo, rejection.get(), redirectAttributes);
        }
        model.addAttribute("number", parsed.text());
        model.addAttribute("quotationLabel", QuotationViews.label(parsed, quotationNo));
        model.addAttribute(
                "quotation",
                QuotationViews.view(
                        quotation, TransportRequestLabels::customerDateTime, now, QuotationViews.Audience.CUSTOMER));
        return RESPONSE_VIEW;
    }

    /** 詳細経路設計へ進む。依頼したら見積依頼の詳細へリダイレクトし、結果を示す（PRG）。 */
    @PostMapping
    public String proceed(
            @PathVariable String number, @PathVariable int quotationNo, RedirectAttributes redirectAttributes) {
        TransportRequestNumber parsed = parse(number);
        RouteDesignRequestOutcome outcome = responseService.requestRouteDesign(
                new RequestRouteDesignCommand(parsed, quotationNo, shipper(), new UserId(provisionalActor.userId())));
        return switch (outcome) {
            case RouteDesignRequestOutcome.Requested(TransportRequestNumber requestedNumber, int requestedNo) ->
                redirectToDetail(
                        parsed,
                        "result",
                        QuotationViews.label(requestedNumber, requestedNo) + " で詳細経路設計を依頼しました",
                        redirectAttributes);
            case RouteDesignRequestOutcome.Rejected(QuotationRejection reason) ->
                rejected(parsed, quotationNo, reason, redirectAttributes);
            case RouteDesignRequestOutcome.Conflict _ ->
                redirectToDetail(parsed, PROBLEM, "見積りが更新されました。最新の見積りを確かめてから回答してください", redirectAttributes);
            case RouteDesignRequestOutcome.NotFound _ -> throw notFound();
        };
    }

    /**
     * 回答を受け付けなかったときに見積依頼の詳細へ戻す。回答済み（二重送信など）は、荷主の望んだ結果が成り立っているので
     * 警告でなく結果として示す（Bolt 12 レビュー R-13）。
     */
    private static String rejected(
            TransportRequestNumber number,
            int quotationNo,
            QuotationRejection reason,
            RedirectAttributes redirectAttributes) {
        if (reason == QuotationRejection.ROUTING_REQUESTED) {
            return redirectToDetail(
                    number,
                    "result",
                    QuotationViews.label(number, quotationNo) + " ですでに詳細経路設計を依頼しています",
                    redirectAttributes);
        }
        return redirectToDetail(number, PROBLEM, rejection(number, quotationNo, reason), redirectAttributes);
    }

    /** 回答を受け付けなかった理由の、荷主向けの文言。荷主には社内の言葉（置換済み）を使わない（Bolt 11 レビュー R-33）。 */
    private static String rejection(TransportRequestNumber number, int quotationNo, QuotationRejection reason) {
        String subject = QuotationViews.label(number, quotationNo);
        return switch (reason) {
            case EXPIRED -> subject + " は有効期限を過ぎて失効しています。新しい見積りは担当営業にご依頼ください";
            case REPLACED -> subject + " は新しい見積りに置き換えられました。見積依頼の詳細で最新の状況をご確認ください";
            case ROUTING_REQUESTED -> subject + " ですでに詳細経路設計を依頼しています";
            case TRANSPORT_REQUEST_NOT_QUOTING, ALREADY_QUOTED, NOT_PENDING_APPROVAL, OUTDATED_VERSION, NOT_PRESENTED ->
                subject + " には回答できません。担当営業にご連絡ください";
        };
    }

    private static String redirectToDetail(
            TransportRequestNumber number, String attribute, String message, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(attribute, message);
        return "redirect:" + BASE_PATH + "/" + number.text();
    }

    private static TransportRequestNumber parse(String number) {
        return TransportRequestViews.parseNumber(number).orElseThrow(QuotationResponseController::notFound);
    }

    private CompanyId shipper() {
        return new CompanyId(provisionalActor.shipperCompanyId());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
