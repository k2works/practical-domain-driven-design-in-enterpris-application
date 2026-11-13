package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.CloseServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReceiveServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.RecordServiceCaseAnswerCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReopenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.UpdateServiceCaseProgressCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.ReceiveServiceCaseOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionService;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseCommandService;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseList;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseListFilter;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseQueryService;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
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
 * S-14 有人案件の一覧と S-15 有人案件の詳細・受領（社内の運用役割。US-20 AC2・AC3。Bolt 40-11）。進捗の更新・回答・終結・再開の操作は Bolt 40-12。
 *
 * <p>業務の判定（権限・期限超過・受領できる状態）は照会とコマンドのサービスが行い、画面は結果を文と行き先に変えるだけにする。形式の誤った受付番号と
 * ない受付番号、権限のない詳細の照会は、ともに 404（存在を明かさない）。権限のない一覧と受領は 403。
 */
@Controller
@RequestMapping("/staff/service-cases")
public class ServiceCaseController {

    private static final String LIST_VIEW = "tracking/staff/service-cases/list";
    private static final String DETAIL_VIEW = "tracking/staff/service-cases/show";
    private static final String RESULT = "result";
    private static final String PROBLEM = "problem";
    private static final String REDIRECT_DETAIL = "redirect:/staff/service-cases/{caseNumber}";
    private static final String CONFLICT_MESSAGE = "ほかの更新が先にこの案件を変更しました。最新の状態を確かめ、必要なら、もう一度受領してください。";

    private final ServiceCaseQueryService queryService;
    private final ServiceCaseCommandService commandService;
    private final ServiceCaseActionService actionService;

    public ServiceCaseController(
            ServiceCaseQueryService queryService,
            ServiceCaseCommandService commandService,
            ServiceCaseActionService actionService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.actionService = actionService;
    }

    /** 有人案件の一覧（S-14）。絞り込みは未終結（既定）・未受領・期限超過・対応中。知らない値は 404。 */
    @GetMapping
    public String list(
            @RequestParam(name = "filter", required = false) String filterParameter,
            AuthenticatedActor actor,
            Model model) {
        ServiceCaseListFilter filter =
                ServiceCaseListFilter.fromParameter(filterParameter).orElseThrow(ServiceCaseController::notFound);
        ServiceCaseList list =
                queryService.list(actor, filter).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        model.addAttribute("filters", ServiceCaseViews.filters(filter));
        model.addAttribute(
                "serviceCases", list.items().stream().map(ServiceCaseViews::row).toList());
        model.addAttribute("emptyMessage", ServiceCaseViews.emptyMessage(filter));
        model.addAttribute("truncated", list.truncated());
        model.addAttribute("limit", list.limit());
        return LIST_VIEW;
    }

    /** 有人案件の詳細（S-15）。受領のボタンは受付済み・上位対応中のときだけ。 */
    @GetMapping("/{caseNumber}")
    public String detail(@PathVariable String caseNumber, AuthenticatedActor actor, Model model) {
        CaseNumber number = ServiceCaseViews.parseNumber(caseNumber).orElseThrow(ServiceCaseController::notFound);
        model.addAttribute(
                "serviceCase",
                queryService
                        .detail(actor, number)
                        .map(found -> ServiceCaseViews.detail(found, actor.userId()))
                        .orElseThrow(ServiceCaseController::notFound));
        return DETAIL_VIEW;
    }

    /** 受領する（US-20 AC2）。担当者と受領時刻はサービスが記録する。PRG で S-15 に戻って結果を示す。 */
    @PostMapping("/{caseNumber}/receive")
    public String receive(@PathVariable String caseNumber, AuthenticatedActor actor, RedirectAttributes redirect) {
        CaseNumber number = ServiceCaseViews.parseNumber(caseNumber).orElseThrow(ServiceCaseController::notFound);
        switch (commandService.receive(actor, new ReceiveServiceCaseCommand(number))) {
            case ReceiveServiceCaseOutcome.Received(CaseNumber received) ->
                redirect.addFlashAttribute(RESULT, received.value() + " を受領しました。対応中にしました。");
            case ReceiveServiceCaseOutcome.NotReceivable(ServiceCaseStatus current) ->
                redirect.addFlashAttribute(
                        PROBLEM, "この案件は受領できる状態ではありません（今の状態: " + ServiceCaseViews.status(current) + "）。最新の状態を確かめてください。");
            case ReceiveServiceCaseOutcome.Conflict _ -> redirect.addFlashAttribute(PROBLEM, CONFLICT_MESSAGE);
            case ReceiveServiceCaseOutcome.NotFound _ -> throw notFound();
            case ReceiveServiceCaseOutcome.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return REDIRECT_DETAIL;
    }

    /** 進捗を更新する（US-20 AC4）。次回更新日時は日本時間の {@code yyyy-MM-dd HH:mm}（空なら変えない）。 */
    @PostMapping("/{caseNumber}/progress")
    public String updateProgress(
            @PathVariable String caseNumber,
            @RequestParam(name = "content", defaultValue = "") String content,
            @RequestParam(name = "nextUpdateAt", defaultValue = "") String nextUpdateAt,
            AuthenticatedActor actor,
            RedirectAttributes redirect) {
        CaseNumber number = parse(caseNumber);
        Optional<UtcInstant> next = ServiceCaseFormConverter.nextUpdate(nextUpdateAt);
        if (!nextUpdateAt.isBlank() && next.isEmpty()) {
            redirect.addFlashAttribute(PROBLEM, "次回更新日時は 2026-11-16 18:00 の形式で入れてください。");
            return REDIRECT_DETAIL;
        }
        return finish(
                actionService.updateProgress(
                        actor, new UpdateServiceCaseProgressCommand(number, content, next.orElse(null))),
                "進捗を更新しました。",
                redirect);
    }

    /** 回答を記録する（US-20 AC5）。 */
    @PostMapping("/{caseNumber}/answer")
    public String recordAnswer(
            @PathVariable String caseNumber,
            @RequestParam(name = "content", defaultValue = "") String content,
            AuthenticatedActor actor,
            RedirectAttributes redirect) {
        return finish(
                actionService.recordAnswer(actor, new RecordServiceCaseAnswerCommand(parse(caseNumber), content)),
                "回答を記録しました。回答済みにしました。",
                redirect);
    }

    /** 終結する（US-20 AC5・AC7）。復旧照合が未完了なら終結できない。 */
    @PostMapping("/{caseNumber}/close")
    public String close(@PathVariable String caseNumber, AuthenticatedActor actor, RedirectAttributes redirect) {
        return finish(actionService.close(actor, new CloseServiceCaseCommand(parse(caseNumber))), "終結しました。", redirect);
    }

    /** 再開する（US-20 AC6）。顧客の回答を最後の回答に記録する。 */
    @PostMapping("/{caseNumber}/reopen")
    public String reopen(
            @PathVariable String caseNumber,
            @RequestParam(name = "customerResponse", defaultValue = "") String customerResponse,
            AuthenticatedActor actor,
            RedirectAttributes redirect) {
        return finish(
                actionService.reopen(actor, new ReopenServiceCaseCommand(parse(caseNumber), customerResponse)),
                "再開しました。対応中にしました。",
                redirect);
    }

    /** 操作の結果を文と行き先にする。権限なしは 403、ない案件は 404、それ以外は S-15 へ戻って結果か警告を示す。 */
    private String finish(ServiceCaseActionOutcome outcome, String doneMessage, RedirectAttributes redirect) {
        switch (outcome) {
            case ServiceCaseActionOutcome.Done _ -> redirect.addFlashAttribute(RESULT, doneMessage);
            case ServiceCaseActionOutcome.InvalidState(ServiceCaseStatus current) ->
                redirect.addFlashAttribute(
                        PROBLEM, "この操作はできる状態ではありません（今の状態: " + ServiceCaseViews.status(current) + "）。最新の状態を確かめてください。");
            case ServiceCaseActionOutcome.InvalidInput(String message) -> redirect.addFlashAttribute(PROBLEM, message);
            case ServiceCaseActionOutcome.ReconciliationIncomplete _ ->
                redirect.addFlashAttribute(PROBLEM, "復旧照合が完了していないため、終結できません。");
            case ServiceCaseActionOutcome.Conflict _ ->
                redirect.addFlashAttribute(PROBLEM, "ほかの更新が先にこの案件を変更しました。最新の状態を確かめ、必要なら、もう一度操作してください。");
            case ServiceCaseActionOutcome.NotFound _ -> throw notFound();
            case ServiceCaseActionOutcome.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return REDIRECT_DETAIL;
    }

    private static CaseNumber parse(String caseNumber) {
        return ServiceCaseViews.parseNumber(caseNumber).orElseThrow(ServiceCaseController::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
