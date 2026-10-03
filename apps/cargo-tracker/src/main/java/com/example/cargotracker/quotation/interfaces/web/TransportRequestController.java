package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ResubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.SendBackNotice;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * 顧客 Web の見積依頼（C-02 見積依頼の一覧、C-03 見積依頼の作成・編集の 1 画面の形、C-04 見積依頼の詳細）。
 * 段階入力は #36 のプロトタイプで操作性を確かめてから入れる。
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
    private static final String UNKNOWN_CONSIGNEE = "（仮の一覧にない荷受人）";

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
            return showCreateForm(model);
        }
        SubmissionOutcome outcome = commandService.submit(
                new SubmitTransportRequestCommand(shipper(), new UserId(provisionalActor.userId()), input.get()));
        return switch (outcome) {
            case SubmissionOutcome.Submitted submitted ->
                redirectToDetail(
                        submitted.number(),
                        TransportRequestLabels.numberWithVersion(submitted.number(), 1) + " を提出しました",
                        redirectAttributes);
            case SubmissionOutcome.Rejected(SubmissionViolations violations) -> {
                SubmissionViolationMessages.reject(violations, bindingResult);
                yield showCreateForm(model);
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
        ShipmentTerms terms = request.currentVersion().terms();
        Cargo cargo = terms.cargo();
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("荷受人", consigneeName(terms));
        rows.put("出発地", terms.origin().unLocode());
        rows.put("目的地", terms.destination().unLocode());
        rows.put("希望到着期限", TransportRequestLabels.customerDateTime(terms.arrivalDeadline()));
        rows.put("貨物種別", TransportRequestLabels.cargoCategory(cargo.category()));
        rows.put("荷姿", TransportRequestLabels.packageType(cargo.packageType()));
        rows.put("個数", String.valueOf(cargo.packageCount()));
        rows.put("総重量（kg）", cargo.grossWeightKg().stripTrailingZeros().toPlainString());
        rows.put("容積（m3）", cargo.volumeM3().stripTrailingZeros().toPlainString());
        rows.put(
                "提出時刻",
                TransportRequestLabels.customerDateTime(request.currentVersion().submittedAt()));
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
        model.addAttribute("editable", request.status() == TransportRequestStatus.DRAFT);
        return DETAIL_VIEW;
    }

    /**
     * C-03 の編集（出し直し）。差し戻されて下書きのときだけ開け、現在の版の輸送条件を初期値にする。
     * 下書きでなければ、詳細に戻して理由を示す。
     */
    @GetMapping("/{number}/edit")
    public String edit(@PathVariable String number, Model model, RedirectAttributes redirectAttributes) {
        TransportRequest request = find(number);
        int versionNo = request.currentVersion().versionNo();
        if (request.status() != TransportRequestStatus.DRAFT) {
            return redirectToDetail(request.number(), notResubmittable(request), redirectAttributes);
        }
        model.addAttribute(
                "transportRequestForm",
                TransportRequestFormConverter.toForm(request.currentVersion().terms()));
        return showEditForm(request.number(), versionNo + 1, model);
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
        TransportRequestNumber transportRequestNumber = parse(number).orElseThrow(TransportRequestController::notFound);
        Optional<ShipmentTermsInput> input =
                TransportRequestFormConverter.convert(transportRequestForm, provisionalConsignees, bindingResult);
        if (input.isEmpty()) {
            return showEditForm(find(number), model);
        }
        ResubmissionOutcome outcome = commandService.resubmit(new ResubmitTransportRequestCommand(
                transportRequestNumber, shipper(), new UserId(provisionalActor.userId()), input.get()));
        return switch (outcome) {
            case ResubmissionOutcome.Resubmitted resubmitted ->
                redirectToDetail(
                        resubmitted.number(),
                        TransportRequestLabels.numberWithVersion(resubmitted.number(), resubmitted.versionNo())
                                + " を出し直しました",
                        redirectAttributes);
            case ResubmissionOutcome.Invalid(SubmissionViolations violations) -> {
                SubmissionViolationMessages.reject(violations, bindingResult);
                yield showEditForm(find(number), model);
            }
            case ResubmissionOutcome.Rejected _ -> {
                TransportRequest current = find(number);
                yield redirectToDetail(current.number(), notResubmittable(current), redirectAttributes);
            }
            case ResubmissionOutcome.Conflict _ ->
                redirectToDetail(transportRequestNumber, "他の利用者が先に更新しました。内容を確かめてから出し直してください", redirectAttributes);
            case ResubmissionOutcome.NotFound _ -> throw notFound();
        };
    }

    private String showCreateForm(Model model) {
        model.addAttribute("formTitle", "見積依頼の作成");
        model.addAttribute("formAction", BASE_PATH);
        model.addAttribute("submitLabel", "提出する");
        return FORM_VIEW;
    }

    private String showEditForm(TransportRequest request, Model model) {
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
        return parse(number)
                .flatMap(found -> queryService.findByNumber(found, shipper()))
                .orElseThrow(TransportRequestController::notFound);
    }

    private String consigneeName(ShipmentTerms terms) {
        return provisionalConsignees
                .find(terms.consigneeCompanyId().value())
                .map(ProvisionalConsigneeProperties.Company::name)
                .orElse(UNKNOWN_CONSIGNEE);
    }

    /** 見積依頼の詳細へリダイレクトし、結果を上部に示す（PRG）。 */
    private static String redirectToDetail(
            TransportRequestNumber number, String result, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(RESULT, result);
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
        return new SendBack(notice.reason(), notice.missingItems() == null ? "（なし）" : notice.missingItems());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static Optional<TransportRequestNumber> parse(String number) {
        try {
            return Optional.of(TransportRequestNumber.parse(number));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
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
