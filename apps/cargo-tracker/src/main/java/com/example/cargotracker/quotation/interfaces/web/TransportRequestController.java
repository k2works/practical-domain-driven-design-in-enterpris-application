package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ResubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.QuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.SendBackNotice;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * 顧客 Web の見積依頼（C-02 見積依頼の一覧、C-03 見積依頼の作成・編集、C-04 見積依頼の詳細）。
 * C-03 は 1 つのフォームの中で 4 つの段階を切り替える段階入力にする（Bolt 8 のプロトタイプで操作性を確かめ、この形で残すと決めた。UI-HO-04）。
 * 業務番号は推測しやすいため、照会と出し直しはすべて荷主企業で絞り、他社の番号は見つからない（404）とする（Q-INV-08、Bolt 4 レビュー R-02）。
 * 荷主企業はいまは仮の主体のもの。認証（US-18）を入れたら、認証の主体に差し替える。
 */
@Controller
@RequestMapping("/customer/transport-requests")
public class TransportRequestController {

    private static final String BASE_PATH = "/customer/transport-requests";
    private static final String LIST_VIEW = "quotation/transport-requests/list";
    private static final String FORM_VIEW = "quotation/transport-requests/new";
    private static final String DETAIL_VIEW = "quotation/transport-requests/detail";
    private static final String RESULT = "result";
    private static final String PROBLEM = "problem";

    private final TransportRequestCommandService commandService;
    private final TransportRequestQueryService queryService;
    private final QuotationQueryService quotationQueryService;
    private final ProvisionalActorProperties provisionalActor;
    private final ProvisionalConsigneeProperties provisionalConsignees;

    public TransportRequestController(
            TransportRequestCommandService commandService,
            TransportRequestQueryService queryService,
            QuotationQueryService quotationQueryService,
            ProvisionalActorProperties provisionalActor,
            ProvisionalConsigneeProperties provisionalConsignees) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.quotationQueryService = quotationQueryService;
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

    /** C-02 見積依頼の一覧。自社の輸送要求を、最初の提出時刻の新しい順に示す。 */
    @GetMapping
    public String list(Model model) {
        model.addAttribute(
                "requests",
                queryService.findSummaries(shipper()).stream()
                        .map(TransportRequestController::row)
                        .toList());
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String newForm(@ModelAttribute TransportRequestForm transportRequestForm, Model model) {
        return showCreateForm(model);
    }

    /**
     * 輸送要求を提出する。形式の誤りと業務の規則の違反は、入力値を残して同じ画面にエラー要約で示す。
     * 提出できたら見積依頼の詳細へリダイレクトし、結果を示す（PRG。完了画面は C-04 に統合した。Bolt 6）。
     */
    @PostMapping
    public String submit(
            @ModelAttribute TransportRequestForm transportRequestForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        Optional<ShipmentTermsInput> input =
                TransportRequestFormConverter.convert(transportRequestForm, provisionalConsignees, bindingResult);
        if (input.isEmpty()) {
            return showCreateFormWithErrors(transportRequestForm, model);
        }
        SubmissionOutcome outcome = commandService.submit(new SubmitTransportRequestCommand(
                shipper(),
                new UserId(provisionalActor.userId()),
                input.get(),
                RequiredDocumentViews.attachments(transportRequestForm)));
        return switch (outcome) {
            case SubmissionOutcome.Submitted submitted ->
                redirectToDetail(
                        submitted.number(),
                        TransportRequestLabels.numberWithVersion(submitted.number(), 1) + " を提出しました",
                        redirectAttributes);
            case SubmissionOutcome.Rejected(SubmissionViolations violations) -> {
                SubmissionViolationMessages.reject(violations, bindingResult);
                yield showCreateFormWithErrors(transportRequestForm, model);
            }
        };
    }

    /**
     * C-04 見積依頼の詳細。URL のキーにも業務番号を使い、内部の ID を出さない（D-4）。
     * 差し戻されているときは、差戻しの理由と不足事項だけを示す。判断者と審査の確定の根拠は見せない（2026-10-03 の人の決定）。
     */
    @GetMapping("/{number}")
    public String detail(@PathVariable String number, Model model) {
        TransportRequest request = find(number);
        Map<String, String> rows = TransportRequestViews.termsRows(
                request.currentVersion(), provisionalConsignees, TransportRequestLabels::customerDateTime);
        model.addAttribute("number", request.number().text());
        model.addAttribute(
                "numberWithVersion",
                TransportRequestLabels.numberWithVersion(
                        request.number(), request.currentVersion().versionNo()));
        model.addAttribute("statusLabel", TransportRequestLabels.customerStatus(request.status()));
        model.addAttribute("guidance", TransportRequestLabels.customerGuidance(request.status()));
        model.addAttribute("terms", rows);
        model.addAttribute(
                "sendBack",
                request.sendBackNotice()
                        .map(TransportRequestController::sendBack)
                        .orElse(null));
        // 出し直せるかは集約が判定する（Bolt 6〜8 レビュー R-13）
        model.addAttribute("editable", request.checkResubmittable().isEmpty());
        model.addAttribute("documents", RequiredDocumentViews.rows(request, BASE_PATH));
        // 見積りの節は提示済みの見積りから出し、輸送要求の状態の更新（DE-03 の受け取り）を待たない（Bolt 10。US-03 AC2）
        model.addAttribute(
                "quotation",
                quotationQueryService
                        .findPresented(request.number(), shipper())
                        .map(quotation -> QuotationViews.view(quotation, TransportRequestLabels::customerDateTime))
                        .orElse(null));
        return DETAIL_VIEW;
    }

    /**
     * 必要書類を取得する（D-20: 提出した荷主は開ける）。荷主企業で絞り、他社の番号・ない書類は 404 にする（Q-INV-08）。
     * ブラウザの中で開かせず、ダウンロードさせる。
     */
    @GetMapping("/{number}/versions/{versionNo}/documents/{documentNo}")
    public ResponseEntity<byte[]> document(
            @PathVariable String number, @PathVariable int versionNo, @PathVariable int documentNo) {
        return TransportRequestViews.parseNumber(number)
                .flatMap(found -> queryService.findDocument(found, shipper(), versionNo, documentNo))
                .map(RequiredDocumentViews::download)
                .orElseThrow(TransportRequestController::notFound);
    }

    /**
     * C-03 の編集（出し直し）。差し戻されて下書きのときだけ開け、現在の版の輸送条件を初期値にする。
     * 下書きでなければ、詳細に戻して理由を示す。
     */
    @GetMapping("/{number}/edit")
    public String edit(@PathVariable String number, Model model, RedirectAttributes redirectAttributes) {
        TransportRequest request = find(number);
        if (request.checkResubmittable().isPresent()) {
            return redirectToDetailWithProblem(request.number(), notResubmittable(request), redirectAttributes);
        }
        model.addAttribute(
                "transportRequestForm",
                TransportRequestFormConverter.toForm(request.currentVersion().terms()));
        return showEditForm(request, model);
    }

    /**
     * 出し直す（新しい版を作る）。期待版（ARCH-HO-01）は画面に持たせない（2026-10-03 の人の決定）。
     * 二重送信の 2 回目は下書きでないため拒否され、同時の更新は楽観ロックで拒否される。どちらも詳細に戻して理由を示す。
     */
    @PostMapping("/{number}/versions")
    public String resubmit(
            @PathVariable String number,
            @ModelAttribute TransportRequestForm transportRequestForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber transportRequestNumber =
                TransportRequestViews.parseNumber(number).orElseThrow(TransportRequestController::notFound);
        Optional<ShipmentTermsInput> input =
                TransportRequestFormConverter.convert(transportRequestForm, provisionalConsignees, bindingResult);
        if (input.isEmpty()) {
            return showEditFormWithErrors(find(number), transportRequestForm, model);
        }
        ResubmissionOutcome outcome = commandService.resubmit(new ResubmitTransportRequestCommand(
                transportRequestNumber,
                shipper(),
                new UserId(provisionalActor.userId()),
                input.get(),
                RequiredDocumentViews.attachments(transportRequestForm)));
        return switch (outcome) {
            case ResubmissionOutcome.Resubmitted(TransportRequestNumber resubmittedNumber, int versionNo) ->
                redirectToDetail(
                        resubmittedNumber,
                        TransportRequestLabels.numberWithVersion(resubmittedNumber, versionNo) + " を出し直しました",
                        redirectAttributes);
            case ResubmissionOutcome.Invalid(SubmissionViolations violations) -> {
                SubmissionViolationMessages.reject(violations, bindingResult);
                yield showEditFormWithErrors(find(number), transportRequestForm, model);
            }
            case ResubmissionOutcome.Rejected _ -> {
                TransportRequest current = find(number);
                yield redirectToDetailWithProblem(current.number(), notResubmittable(current), redirectAttributes);
            }
            case ResubmissionOutcome.Conflict _ ->
                redirectToDetailWithProblem(
                        transportRequestNumber, "他の利用者が先に更新しました。内容を確かめてから出し直してください", redirectAttributes);
            case ResubmissionOutcome.NotFound _ -> throw notFound();
        };
    }

    private String showCreateForm(Model model) {
        model.addAttribute("formTitle", "見積依頼の作成");
        model.addAttribute("formAction", BASE_PATH);
        model.addAttribute("submitLabel", "提出する");
        return FORM_VIEW;
    }

    /** 誤りのある作成画面。ファイルを選んでいたら、選び直しを案内する（R-02）。 */
    private String showCreateFormWithErrors(TransportRequestForm form, Model model) {
        model.addAttribute("reselectDocuments", RequiredDocumentViews.hasSelectedFiles(form));
        return showCreateForm(model);
    }

    /** 誤りのある編集画面。ファイルを選んでいたら、選び直しを案内する（R-02）。 */
    private String showEditFormWithErrors(TransportRequest request, TransportRequestForm form, Model model) {
        model.addAttribute("reselectDocuments", RequiredDocumentViews.hasSelectedFiles(form));
        return showEditForm(request, model);
    }

    /** 編集画面。前の版の書類を一覧で示し、選ばなかった種類は引き継ぎ、選んだ種類は差し替えることを案内する（D-25）。 */
    private String showEditForm(TransportRequest request, Model model) {
        model.addAttribute("carriedDocuments", RequiredDocumentViews.rows(request, BASE_PATH));
        return showEditForm(request.number(), request.currentVersion().versionNo() + 1, model);
    }

    private String showEditForm(TransportRequestNumber number, int nextVersionNo, Model model) {
        model.addAttribute("formTitle", "見積依頼の編集");
        model.addAttribute("formAction", BASE_PATH + "/" + number.text() + "/versions");
        model.addAttribute("submitLabel", "出し直す");
        model.addAttribute(
                "versionNotice", number.text() + " を直して出し直します。出し直すと版 " + nextVersionNo + " になります。業務番号は変わりません。");
        return FORM_VIEW;
    }

    private CompanyId shipper() {
        return new CompanyId(provisionalActor.shipperCompanyId());
    }

    private TransportRequest find(String number) {
        return TransportRequestViews.parseNumber(number)
                .flatMap(found -> queryService.findByNumber(found, shipper()))
                .orElseThrow(TransportRequestController::notFound);
    }

    /** 見積依頼の詳細へリダイレクトし、結果を上部に示す（PRG）。 */
    private static String redirectToDetail(
            TransportRequestNumber number, String result, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(RESULT, result);
        return "redirect:" + BASE_PATH + "/" + number.text();
    }

    /** 操作できなかった理由を、結果と分けて詳細の上部に警告として示す（Bolt 6〜8 レビュー R-24）。 */
    private static String redirectToDetailWithProblem(
            TransportRequestNumber number, String problem, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(PROBLEM, problem);
        return "redirect:" + BASE_PATH + "/" + number.text();
    }

    /** 下書きでないため出し直せない理由（例: TR-2026-0001 版 2 は審査中のため出し直せません）。二重送信の 2 回目もこれになる。 */
    private static String notResubmittable(TransportRequest request) {
        return TransportRequestLabels.numberWithVersion(
                        request.number(), request.currentVersion().versionNo())
                + " は" + TransportRequestLabels.status(request.status()) + "のため出し直せません";
    }

    private static Row row(TransportRequestSummary summary) {
        return new Row(
                summary.number().text(),
                summary.versionNo(),
                TransportRequestLabels.customerStatus(summary.status()),
                summary.origin().unLocode() + " → " + summary.destination().unLocode(),
                TransportRequestLabels.customerDateTime(summary.firstSubmittedAt()));
    }

    private static SendBack sendBack(SendBackNotice notice) {
        return new SendBack(notice.reason(), TransportRequestLabels.missingItems(notice.missingItems()));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /**
     * 見積依頼の一覧の 1 行。
     *
     * @param number 業務番号
     * @param versionNo 版番号
     * @param status 状態（いま誰の対応待ちか）
     * @param route 出発地 → 目的地
     * @param firstSubmittedAt 最初の提出時刻（荷主の日時表示）
     */
    public record Row(String number, int versionNo, String status, String route, String firstSubmittedAt) {}

    /**
     * 荷主に見せる差戻し。
     *
     * @param reason 差戻しの理由
     * @param missingItems 不足事項（なければ「（なし）」）
     */
    public record SendBack(String reason, String missingItems) {}
}
