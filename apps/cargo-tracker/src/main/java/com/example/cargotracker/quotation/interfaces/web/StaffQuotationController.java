package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.CalculationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.PresentationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 社内業務 Web の見積りの作成（S-04。US-03）。料金明細・通貨・有効期限・経路方針を 1 つの画面で入れて算出し（承認待ち）、
 * 算出した見積りを確かめて社内承認して提示する。社内の画面なので荷主企業で絞らない。社内承認者は、認証（US-18）までは仮の営業担当者。
 */
@Controller
@RequestMapping("/staff/transport-requests/{number}/quotations")
public class StaffQuotationController {

    private static final String LIST_PATH = "/staff/transport-requests";
    private static final String FORM_VIEW = "quotation/staff/quotations/new";
    private static final String SHOW_VIEW = "quotation/staff/quotations/show";
    private static final String RESULT = "result";
    private static final String PROBLEM = "problem";

    private final QuotationCommandService commandService;
    private final StaffQuotationQueryService queryService;
    private final StaffTransportRequestQueryService transportRequestQueryService;
    private final ProvisionalActorProperties provisionalActor;
    private final ProvisionalConsigneeProperties provisionalConsignees;

    public StaffQuotationController(
            QuotationCommandService commandService,
            StaffQuotationQueryService queryService,
            StaffTransportRequestQueryService transportRequestQueryService,
            ProvisionalActorProperties provisionalActor,
            ProvisionalConsigneeProperties provisionalConsignees) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.transportRequestQueryService = transportRequestQueryService;
        this.provisionalActor = provisionalActor;
        this.provisionalConsignees = provisionalConsignees;
    }

    /** S-04 見積りの作成。 */
    @GetMapping("/new")
    public String newQuotation(@PathVariable String number, Model model) {
        model.addAttribute("quotationForm", new QuotationForm());
        return showForm(findTransportRequest(number), model);
    }

    /** 見積りを作って算出する。算出したら算出した見積りの画面へ移り（PRG）、誤りがあれば入力を残してエラー要約で示す（AC3）。 */
    @PostMapping
    public String calculate(
            @PathVariable String number,
            @ModelAttribute QuotationForm quotationForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequest request = findTransportRequest(number);
        List<Integer> rowsOfLines = new ArrayList<>();
        Optional<QuotationInput> input = QuotationFormConverter.convert(quotationForm, bindingResult, rowsOfLines);
        if (input.isEmpty()) {
            return showForm(request, model);
        }
        return switch (commandService.calculate(new CalculateQuotationCommand(request.number(), input.get()))) {
            case CalculationOutcome.Calculated(TransportRequestNumber calculated, int quotationNo) -> {
                redirectAttributes.addFlashAttribute(
                        RESULT, QuotationViews.label(calculated, quotationNo) + " を算出しました。内容を確かめて社内承認してください");
                yield "redirect:" + quotationPath(calculated, quotationNo);
            }
            case CalculationOutcome.Invalid(QuotationViolations violations) -> {
                QuotationViolationMessages.reject(violations, bindingResult, rowsOfLines);
                yield showForm(request, model);
            }
            case CalculationOutcome.Rejected(QuotationRejection reason) -> {
                redirectAttributes.addFlashAttribute(PROBLEM, rejection(request.number(), reason));
                yield "redirect:" + LIST_PATH;
            }
            case CalculationOutcome.NotFound _ -> throw notFound();
        };
    }

    /** 算出した見積り。承認待ちなら、社内承認して提示するボタンを出す。 */
    @GetMapping("/{quotationNo}")
    public String show(@PathVariable String number, @PathVariable int quotationNo, Model model) {
        TransportRequest request = findTransportRequest(number);
        Quotation quotation =
                queryService.find(request.number(), quotationNo).orElseThrow(StaffQuotationController::notFound);
        model.addAttribute("number", request.number().text());
        model.addAttribute("quotationLabel", QuotationViews.label(request.number(), quotationNo));
        model.addAttribute(
                "numberWithVersion",
                TransportRequestLabels.numberWithVersion(request.number(), quotation.transportRequestVersionNo()));
        model.addAttribute("quotation", QuotationViews.view(quotation, TransportRequestLabels::staffDateTime));
        return SHOW_VIEW;
    }

    /** 社内承認して提示する。提示したら受付一覧へ戻り、結果を示す（PRG）。 */
    @PostMapping("/{quotationNo}/presentation")
    public String present(
            @PathVariable String number, @PathVariable int quotationNo, RedirectAttributes redirectAttributes) {
        TransportRequestNumber transportRequestNumber =
                TransportRequestViews.parseNumber(number).orElseThrow(StaffQuotationController::notFound);
        UserId approver = new UserId(provisionalActor.staffUserId());
        return switch (commandService.present(
                new PresentQuotationCommand(transportRequestNumber, quotationNo, approver))) {
            case PresentationOutcome.Presented(TransportRequestNumber presented, int presentedNo) -> {
                redirectAttributes.addFlashAttribute(RESULT, QuotationViews.label(presented, presentedNo) + " を提示しました");
                yield "redirect:" + LIST_PATH;
            }
            case PresentationOutcome.Rejected _ -> {
                redirectAttributes.addFlashAttribute(
                        PROBLEM, QuotationViews.label(transportRequestNumber, quotationNo) + " は承認待ちでないため提示できません");
                yield "redirect:" + quotationPath(transportRequestNumber, quotationNo);
            }
            case PresentationOutcome.Conflict _ -> {
                redirectAttributes.addFlashAttribute(PROBLEM, "他の利用者が先に更新しました。内容を確かめてください");
                yield "redirect:" + quotationPath(transportRequestNumber, quotationNo);
            }
            case PresentationOutcome.NotFound _ -> throw notFound();
        };
    }

    private String showForm(TransportRequest request, Model model) {
        model.addAttribute("number", request.number().text());
        model.addAttribute(
                "numberWithVersion",
                TransportRequestLabels.numberWithVersion(
                        request.number(), request.currentVersion().versionNo()));
        model.addAttribute(
                "terms",
                TransportRequestViews.termsRows(
                        request.currentVersion(), provisionalConsignees, TransportRequestLabels::staffDateTime));
        model.addAttribute("fieldLabels", QuotationViolationMessages.fieldLabels());
        return FORM_VIEW;
    }

    private TransportRequest findTransportRequest(String number) {
        return TransportRequestViews.parseNumber(number)
                .flatMap(transportRequestQueryService::findByNumber)
                .orElseThrow(StaffQuotationController::notFound);
    }

    private static String quotationPath(TransportRequestNumber number, int quotationNo) {
        return LIST_PATH + "/" + number.text() + "/quotations/" + quotationNo;
    }

    private static String rejection(TransportRequestNumber number, QuotationRejection reason) {
        return switch (reason) {
            case TRANSPORT_REQUEST_NOT_QUOTING -> number.text() + " は見積り作成中でないため、見積りを作れません";
            case ALREADY_QUOTED -> number.text() + " にはすでに見積りがあります";
            case NOT_PENDING_APPROVAL -> number.text() + " の見積りは承認待ちではありません";
        };
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
