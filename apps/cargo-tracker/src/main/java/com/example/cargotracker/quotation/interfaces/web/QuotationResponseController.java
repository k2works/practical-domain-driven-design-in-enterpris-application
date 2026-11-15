package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.ConsultQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.DeclineQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ConsultOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.DeclineOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteDesignRequestOutcome;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * C-05 見積りへの回答（US-24 AC1・AC2。Bolt 12・W9 の Bolt 46）。荷主担当者が見積りと経路方針を確かめ、詳細経路設計へ進むか、理由を付けて
 * 辞退する。相談（AC3）は後の Bolt で、それまでは担当営業への連絡を案内する。確認の領域は挟まない（C-05 自体が確かめる画面。2026-10-06 の決定）。
 * 荷主企業と回答者は、ログインした荷主担当者（認証の主体 {@link AuthenticatedActor}。ADR-012）のもの。
 */
@Controller
@RequestMapping("/customer/transport-requests/{number}/quotations/{quotationNo}/response")
public class QuotationResponseController {

    private static final String BASE_PATH = "/customer/transport-requests";
    private static final String RESPONSE_VIEW = "quotation/transport-requests/response";
    private static final String PROBLEM = "problem";
    private static final String CONFIRMED = "yes";
    private static final String RESULT = "result";

    private final QuotationResponseService responseService;
    private final QuotationQueryService quotationQueryService;
    private final Clock clock;

    public QuotationResponseController(
            QuotationResponseService responseService, QuotationQueryService quotationQueryService, Clock clock) {
        this.responseService = responseService;
        this.quotationQueryService = quotationQueryService;
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
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber parsed = parse(number);
        Quotation quotation = quotationQueryService.findVisible(parsed, actor.companyId()).stream()
                .filter(candidate -> candidate.quotationNo() == quotationNo)
                .findFirst()
                .orElseThrow(QuotationResponseController::notFound);
        UtcInstant now = new UtcInstant(clock.instant());
        Optional<QuotationRejection> rejection = quotation.responseRejectionAt(now);
        if (rejection.isPresent()) {
            return rejected(parsed, quotationNo, rejection.get(), redirectAttributes);
        }
        return responseView(parsed, quotationNo, quotation, now, model);
    }

    private static String responseView(
            TransportRequestNumber parsed, int quotationNo, Quotation quotation, UtcInstant now, Model model) {
        model.addAttribute("number", parsed.text());
        model.addAttribute("quotationLabel", QuotationViews.label(parsed, quotationNo));
        model.addAttribute(
                "quotation",
                QuotationViews.view(
                        quotation, TransportRequestLabels::customerDateTime, now, QuotationViews.Audience.CUSTOMER));
        return RESPONSE_VIEW;
    }

    /**
     * 理由を付けて辞退する（US-24 AC2。W9 の Bolt 46）。辞退したら見積依頼の詳細へリダイレクトして結果を示す（PRG）。理由の誤り（空・長すぎる）は
     * 回答の画面をそのまま描き直して、入力した理由を残す。
     */
    @PostMapping("/decline")
    public String decline(
            @PathVariable String number,
            @PathVariable int quotationNo,
            @RequestParam(name = "reason", defaultValue = "") String reason,
            @RequestParam(name = "confirm", defaultValue = "") String confirm,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber parsed = parse(number);
        // 辞退は取り消せないので、確認のチェックがなければ受け付けず、入力した理由を残して画面を描き直す（W9 の週全体のレビュー。ユーザー代表）
        if (!CONFIRMED.equals(confirm)) {
            model.addAttribute("declineProblem", "取り消せないことを確かめて、チェックを入れてください");
            model.addAttribute("declineReason", reason);
            return redisplay(parsed, quotationNo, actor, model);
        }
        DeclineOutcome outcome = responseService.decline(
                new DeclineQuotationCommand(parsed, quotationNo, actor.companyId(), actor.userId(), reason));
        return switch (outcome) {
            case DeclineOutcome.Declined(TransportRequestNumber declinedNumber, int declinedNo) ->
                redirectToDetail(
                        parsed,
                        RESULT,
                        QuotationViews.label(declinedNumber, declinedNo) + " を辞退しました。理由は担当営業に伝わります",
                        redirectAttributes);
            case DeclineOutcome.InvalidReason(String message) -> {
                model.addAttribute("declineProblem", message);
                model.addAttribute("declineReason", reason);
                yield redisplay(parsed, quotationNo, actor, model);
            }
            case DeclineOutcome.Rejected(QuotationRejection declinedReason) ->
                isAlreadyDeclined(parsed, quotationNo, actor)
                        // 辞退の二重送信は失効（期限切れ）でなく、辞退済みという結果として示す
                        ? redirectToDetail(
                                parsed,
                                RESULT,
                                QuotationViews.label(parsed, quotationNo) + " はすでに辞退しています",
                                redirectAttributes)
                        : redirectToDetail(
                                parsed,
                                PROBLEM,
                                declineRejection(parsed, quotationNo, declinedReason),
                                redirectAttributes);
            case DeclineOutcome.Conflict _ ->
                redirectToDetail(parsed, PROBLEM, "見積りが更新されました。最新の見積りを確かめてから回答してください", redirectAttributes);
            case DeclineOutcome.NotFound _ -> throw notFound();
        };
    }

    /**
     * 見積りの条件を相談する（US-24 AC3。W9 の Bolt 46-1）。相談すると見積りの状態は変えず、有人案件（見積り相談）を起票する（追跡が購読して
     * 別のトランザクションで起票する）。見積依頼の詳細へリダイレクトして結果を示す（PRG）。内容の誤りは回答の画面を描き直して、入力を残す。
     */
    @PostMapping("/consult")
    public String consult(
            @PathVariable String number,
            @PathVariable int quotationNo,
            @RequestParam(name = "content", defaultValue = "") String content,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber parsed = parse(number);
        ConsultOutcome outcome = responseService.consult(
                new ConsultQuotationCommand(parsed, quotationNo, actor.companyId(), actor.userId(), content));
        return switch (outcome) {
            case ConsultOutcome.Requested(TransportRequestNumber requestedNumber, int requestedNo) ->
                redirectToDetail(
                        parsed,
                        RESULT,
                        QuotationViews.label(requestedNumber, requestedNo)
                                + " の条件の相談を受け付けました。担当者が確認します。進捗は「問い合わせ」で確かめられます",
                        redirectAttributes);
            case ConsultOutcome.InvalidContent(String message) -> {
                model.addAttribute("consultProblem", message);
                model.addAttribute("consultContent", content);
                yield redisplay(parsed, quotationNo, actor, model);
            }
            case ConsultOutcome.Rejected(QuotationRejection consultReason) ->
                redirectToDetail(
                        parsed, PROBLEM, consultRejection(parsed, quotationNo, consultReason), redirectAttributes);
            case ConsultOutcome.NotFound _ -> throw notFound();
        };
    }

    /** 相談を受け付けなかった理由の、荷主向けの文言。回答済みは相談できない旨を示し、ほかは回答と同じ文言にする。 */
    private static String consultRejection(TransportRequestNumber number, int quotationNo, QuotationRejection reason) {
        if (reason == QuotationRejection.ROUTING_REQUESTED) {
            return QuotationViews.label(number, quotationNo) + " はすでに詳細経路設計を依頼しているため相談できません。担当営業にご連絡ください";
        }
        return rejection(number, quotationNo, reason);
    }

    /** 回答の画面を、荷主に提示した見積りで描き直す（入力の誤りを示すとき）。 */
    private String redisplay(TransportRequestNumber parsed, int quotationNo, AuthenticatedActor actor, Model model) {
        Quotation quotation = findVisible(parsed, quotationNo, actor);
        return responseView(parsed, quotationNo, quotation, new UtcInstant(clock.instant()), model);
    }

    private Quotation findVisible(TransportRequestNumber parsed, int quotationNo, AuthenticatedActor actor) {
        return quotationQueryService.findVisible(parsed, actor.companyId()).stream()
                .filter(candidate -> candidate.quotationNo() == quotationNo)
                .findFirst()
                .orElseThrow(QuotationResponseController::notFound);
    }

    private boolean isAlreadyDeclined(TransportRequestNumber parsed, int quotationNo, AuthenticatedActor actor) {
        return quotationQueryService.findVisible(parsed, actor.companyId()).stream()
                .anyMatch(candidate -> candidate.quotationNo() == quotationNo && candidate.isDeclined());
    }

    /** 辞退を受け付けなかった理由の、荷主向けの文言。回答済みは辞退できない旨を示し、ほかは回答と同じ文言にする。 */
    private static String declineRejection(TransportRequestNumber number, int quotationNo, QuotationRejection reason) {
        if (reason == QuotationRejection.ROUTING_REQUESTED) {
            return QuotationViews.label(number, quotationNo) + " はすでに詳細経路設計を依頼しているため辞退できません。担当営業にご連絡ください";
        }
        return rejection(number, quotationNo, reason);
    }

    /** 詳細経路設計へ進む。依頼したら見積依頼の詳細へリダイレクトし、結果を示す（PRG）。 */
    @PostMapping
    public String proceed(
            @PathVariable String number,
            @PathVariable int quotationNo,
            AuthenticatedActor actor,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber parsed = parse(number);
        RouteDesignRequestOutcome outcome = responseService.requestRouteDesign(
                new RequestRouteDesignCommand(parsed, quotationNo, actor.companyId(), actor.userId()));
        return switch (outcome) {
            case RouteDesignRequestOutcome.Requested(TransportRequestNumber requestedNumber, int requestedNo) ->
                redirectToDetail(
                        parsed,
                        RESULT,
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
                    RESULT,
                    QuotationViews.label(number, quotationNo) + " ですでに詳細経路設計を依頼しています",
                    redirectAttributes);
        }
        return redirectToDetail(number, PROBLEM, rejection(number, quotationNo, reason), redirectAttributes);
    }

    /** 回答を受け付けなかった理由の、荷主向けの文言。荷主には社内の言葉（置換済み）を使わない（Bolt 11 レビュー R-33）。 */
    private static String rejection(TransportRequestNumber number, int quotationNo, QuotationRejection reason) {
        String subject = QuotationViews.label(number, quotationNo);
        // APPROVED_AND_STILL_VALID・TRANSPORT_REQUEST_BOOKED（再見積り。Bolt 35b・35c）と
        // NOT_APPROVED（予約確定の照会）は荷主の操作では返らず、網羅のために既定の組に入れる。ROUTE_REDESIGN_REQUIRED は荷主の
        // 承認で返るが、回答（詳細経路設計へ進む）では返らない（Bolt 37e）。ROUTE_ALREADY_DEPARTED も同じ（D-88。Bolt 37e4）
        return switch (reason) {
            case EXPIRED -> subject + " は有効期限を過ぎて失効しています。新しい見積りは担当営業にご依頼ください";
            case REPLACED -> subject + " は新しい見積りに置き換えられました。見積依頼の詳細で最新の状況をご確認ください";
            case ROUTING_REQUESTED -> subject + " ですでに詳細経路設計を依頼しています";
            case TRANSPORT_REQUEST_NOT_QUOTING,
                    TRANSPORT_REQUEST_BOOKED,
                    ALREADY_QUOTED,
                    NOT_PENDING_APPROVAL,
                    OUTDATED_VERSION,
                    NOT_PRESENTED,
                    NOT_AWAITING_SHIPPER_APPROVAL,
                    ALREADY_APPROVED,
                    NOT_APPROVED,
                    APPROVED_AND_STILL_VALID,
                    ROUTE_REDESIGN_REQUIRED,
                    ROUTE_ALREADY_DEPARTED -> subject + " には回答できません。担当営業にご連絡ください";
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

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
