package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.tracking.application.internal.commands.ReopenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionService;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerServiceCaseItem;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerServiceCaseList;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerServiceCaseLookup;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseQueryService;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.entities.CaseAnswer;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import java.time.Instant;
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
 * C-14 問い合わせ一覧と C-15 問い合わせの詳細・再開の依頼（荷主担当者。US-23。Bolt 42-2・42-3）。自社の問い合わせ（有人案件）だけを示す。
 * 企業での絞り込みは照会のサービスが担い、この画面は企業を比べない。他社の問い合わせを開く試行と再開の依頼は、内容を返さず 404 にして、試行を
 * 記録する（AC3。{@link TrackingOutOfScopeResponder}）。ない・形式の誤りの受付番号は記録せず同じ 404。URL の規則（{@code /customer/**} は
 * 荷主担当者だけ）がほかの役割を止める。
 */
@Controller
@RequestMapping(CustomerServiceCaseController.BASE_PATH)
public class CustomerServiceCaseController {

    static final String BASE_PATH = "/customer/inquiries";
    private static final String LIST_VIEW = "tracking/customer/service-cases/list";
    private static final String DETAIL_VIEW = "tracking/customer/service-cases/show";
    private static final String REDIRECT_DETAIL = "redirect:" + BASE_PATH + "/{caseNumber}";
    private static final String RESULT = "result";
    private static final String PROBLEM = "problem";

    private final ServiceCaseQueryService queryService;
    private final ServiceCaseActionService actionService;
    private final TrackingOutOfScopeResponder outOfScopeResponder;

    public CustomerServiceCaseController(
            ServiceCaseQueryService queryService,
            ServiceCaseActionService actionService,
            TrackingOutOfScopeResponder outOfScopeResponder) {
        this.queryService = queryService;
        this.actionService = actionService;
        this.outOfScopeResponder = outOfScopeResponder;
    }

    @GetMapping
    public String list(AuthenticatedActor actor, Model model) {
        CustomerServiceCaseList list = queryService.customerList(actor);
        model.addAttribute("serviceCases", rows(list.items()));
        model.addAttribute("truncated", list.truncated());
        model.addAttribute("limit", list.limit());
        // 問い合わせの窓口の受付時間（既定の営業カレンダー。平日 9:00〜18:00。台帳 D-164。仮）を、荷主の日時表示と同じ時間帯で示す（AC4）
        model.addAttribute("receptionHours", receptionHours());
        return LIST_VIEW;
    }

    /** 問い合わせの窓口の受付時間の文（荷主の日時表示と同じ時間帯。追跡の照会の担当窓口も使う。台帳 D-164。仮）。 */
    static String receptionHours() {
        return "平日 09:00〜18:00 " + DateTimeDisplay.customerZoneLabel(Instant.now());
    }

    /** 問い合わせの詳細（進捗と回答の履歴）。回答済みなら再開の依頼の欄を示す。 */
    @GetMapping("/{caseNumber}")
    public String detail(@PathVariable String caseNumber, AuthenticatedActor actor, Model model) {
        ServiceCase serviceCase = open(caseNumber, actor);
        model.addAttribute("inquiry", detailView(serviceCase));
        return DETAIL_VIEW;
    }

    /** 未解決と答えて再開を依頼する（US-23 AC2）。同じ受付番号で再開し、過去の回答の履歴を保持する。 */
    @PostMapping("/{caseNumber}/reopen")
    public String reopen(
            @PathVariable String caseNumber,
            @RequestParam(name = "customerResponse", defaultValue = "") String customerResponse,
            AuthenticatedActor actor,
            RedirectAttributes redirect) {
        // 他社の案件の再開の依頼も、試行として記録して 404 にする（サービスは他社とない案件を区別しないので、先に確かめる）
        open(caseNumber, actor);
        CaseNumber number = ServiceCaseViews.parseNumber(caseNumber).orElseThrow();
        switch (actionService.reopen(actor, new ReopenServiceCaseCommand(number, customerResponse))) {
            case ServiceCaseActionOutcome.Done _ -> redirect.addFlashAttribute(RESULT, "再開を依頼しました。担当者が対応します。");
            case ServiceCaseActionOutcome.InvalidState _ ->
                redirect.addFlashAttribute(PROBLEM, "いまは再開を依頼できません。最新の状態を確かめてください。");
            case ServiceCaseActionOutcome.InvalidInput(String message) -> redirect.addFlashAttribute(PROBLEM, message);
            case ServiceCaseActionOutcome.Conflict _ ->
                redirect.addFlashAttribute(PROBLEM, "ほかの更新が先にこの問い合わせを変更しました。最新の状態を確かめ、必要なら、もう一度操作してください。");
            case ServiceCaseActionOutcome.NotFound _, ServiceCaseActionOutcome.NotPermitted _ ->
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case ServiceCaseActionOutcome.ReconciliationIncomplete _ -> throw new IllegalStateException("再開で復旧照合は起きない");
        }
        redirect.addAttribute("caseNumber", caseNumber);
        return REDIRECT_DETAIL;
    }

    /** 自社の問い合わせを開く。他社は試行を記録して 404、ない・形式の誤りは記録せず 404。 */
    private ServiceCase open(String caseNumber, AuthenticatedActor actor) {
        CaseNumber number = ServiceCaseViews.parseNumber(caseNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return switch (queryService.customerDetail(actor, number)) {
            case CustomerServiceCaseLookup.Found(var detail) -> detail.serviceCase();
            case CustomerServiceCaseLookup.OutOfScope _ ->
                throw outOfScopeResponder.recordServiceCaseAndNotFound(actor, number.value());
            case CustomerServiceCaseLookup.NotFound _ -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
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

    /** 詳細の履歴の 1 行。担当者の利用者 ID は荷主に出さない。 */
    record ProgressRow(String at, String content) {}

    record AnswerRow(String at, String content, String customerResponse) {}

    record DetailView(
            String number,
            String status,
            String nextUpdateAt,
            boolean reopenable,
            List<ProgressRow> progress,
            List<AnswerRow> answers) {}

    private static DetailView detailView(ServiceCase c) {
        return new DetailView(
                c.number().value(),
                ServiceCaseViews.status(c.status()),
                DateTimeDisplay.customer(c.nextUpdateAt().instant()),
                c.status() == ServiceCaseStatus.ANSWERED || c.status() == ServiceCaseStatus.CLOSED,
                c.progress().stream()
                        .map(p -> new ProgressRow(
                                DateTimeDisplay.customer(p.updatedAt().instant()), p.content()))
                        .toList(),
                c.answers().stream()
                        .map(a -> new AnswerRow(
                                DateTimeDisplay.customer(a.answeredAt().instant()),
                                a.content(),
                                a.customerResponse().orElse("")))
                        .toList());
    }
}
