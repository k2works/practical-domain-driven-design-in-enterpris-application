package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
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

/**
 * 顧客 Web の見積依頼（C-03 見積依頼の作成・編集の 1 画面の形、Bolt 4）。
 * 段階入力は #36 のプロトタイプで操作性を確かめてから入れる。
 */
@Controller
@RequestMapping("/customer/transport-requests")
public class TransportRequestController {

    private static final String FORM_VIEW = "quotation/transport-requests/new";

    private final TransportRequestCommandService commandService;
    private final TransportRequestQueryService queryService;
    private final ProvisionalActorProperties provisionalActor;
    private final ProvisionalConsigneeProperties provisionalConsignees;

    public TransportRequestController(
            TransportRequestCommandService commandService,
            TransportRequestQueryService queryService,
            ProvisionalActorProperties provisionalActor,
            ProvisionalConsigneeProperties provisionalConsignees) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.provisionalActor = provisionalActor;
        this.provisionalConsignees = provisionalConsignees;
    }

    @ModelAttribute
    void formOptions(Model model) {
        model.addAttribute("consignees", provisionalConsignees.companies());
        model.addAttribute("cargoCategories", TransportRequestLabels.CARGO_CATEGORIES);
        model.addAttribute("packageTypes", TransportRequestLabels.PACKAGE_TYPES);
        model.addAttribute("cargoCategoryNotice", SubmissionViolationMessages.CargoCategoryNotice.MESSAGE);
        model.addAttribute("fieldLabels", SubmissionViolationMessages.FIELD_LABELS);
    }

    @GetMapping("/new")
    public String newForm(@ModelAttribute TransportRequestForm transportRequestForm) {
        return FORM_VIEW;
    }

    /**
     * 輸送要求を提出する。形式の誤りと業務の規則の違反は、入力値を残して同じ画面にエラー要約で示す。
     * 提出できたら業務番号の完了画面へリダイレクトする（PRG）。
     */
    @PostMapping
    public String submit(@ModelAttribute TransportRequestForm transportRequestForm, BindingResult bindingResult) {
        Optional<ShipmentTermsInput> input =
                TransportRequestFormConverter.convert(transportRequestForm, provisionalConsignees, bindingResult);
        if (input.isEmpty()) {
            return FORM_VIEW;
        }
        SubmissionOutcome outcome = commandService.submit(new SubmitTransportRequestCommand(
                new CompanyId(provisionalActor.shipperCompanyId()),
                new UserId(provisionalActor.userId()),
                input.get()));
        return switch (outcome) {
            case SubmissionOutcome.Submitted submitted -> redirectToSubmitted(submitted);
            case SubmissionOutcome.Rejected(SubmissionViolations violations) -> {
                SubmissionViolationMessages.reject(violations, bindingResult);
                yield FORM_VIEW;
            }
        };
    }

    /**
     * 提出の完了画面。URL のキーにも業務番号を使い、内部の ID を出さない（D-4）。
     * 業務番号は推測しやすいため、荷主企業で絞り、他社の番号は見つからない（404）とする（Bolt 4 レビュー R-02）。
     * 荷主企業はいまは仮の主体のもの。認証（US-18）を入れたら、認証の主体に差し替える。
     */
    @GetMapping("/{number}/submitted")
    public String submitted(@PathVariable String number, Model model) {
        TransportRequest transportRequest = parse(number)
                .flatMap(found -> queryService.findByNumber(found, new CompanyId(provisionalActor.shipperCompanyId())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute(
                "numberWithVersion",
                transportRequest.number().text() + " 版 "
                        + transportRequest.currentVersion().versionNo());
        model.addAttribute("statusLabel", TransportRequestLabels.status(transportRequest.status()));
        model.addAttribute(
                "submittedAt",
                TransportRequestLabels.customerDateTime(
                        transportRequest.currentVersion().submittedAt()));
        return "quotation/transport-requests/submitted";
    }

    /** 業務番号の完了画面へリダイレクトする（PRG）。 */
    private static String redirectToSubmitted(SubmissionOutcome.Submitted submitted) {
        return "redirect:/customer/transport-requests/" + submitted.number().text() + "/submitted";
    }

    private static Optional<TransportRequestNumber> parse(String number) {
        try {
            return Optional.of(TransportRequestNumber.parse(number));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
