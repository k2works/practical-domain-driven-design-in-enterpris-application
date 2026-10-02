package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

/**
 * 顧客 Web の見積依頼（C-03 見積依頼の作成・編集の 1 画面の形、Bolt 4）。
 * 段階入力は #36 のプロトタイプで操作性を確かめてから入れる。
 */
@Controller
@RequestMapping("/customer/transport-requests")
public class TransportRequestController {

    private static final String FORM_VIEW = "quotation/transport-requests/new";

    /** 日時表示の共通部品（UI 設計）: 「年月日 時刻 タイムゾーン（UTC offset）」。 */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV（'UTC'xxx）");

    private static final List<Option> CARGO_CATEGORIES = List.of(
            new Option(CargoCategory.GENERAL.name(), "一般"),
            new Option(CargoCategory.DANGEROUS.name(), "危険物"),
            new Option(CargoCategory.REEFER.name(), "冷凍"),
            new Option(CargoCategory.OTHER_SPECIAL.name(), "その他特殊"));

    private static final List<Option> PACKAGE_TYPES = List.of(
            new Option(PackageType.PALLET.name(), "パレット"),
            new Option(PackageType.CARTON.name(), "カートン"),
            new Option(PackageType.CRATE.name(), "クレート"),
            new Option(PackageType.OTHER.name(), "その他"));

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
        model.addAttribute("cargoCategories", CARGO_CATEGORIES);
        model.addAttribute("packageTypes", PACKAGE_TYPES);
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
            case SubmissionOutcome.Submitted submitted ->
                "redirect:/customer/transport-requests/" + submitted.number().text() + "/submitted";
            case SubmissionOutcome.Rejected rejected -> {
                SubmissionViolationMessages.reject(rejected.violations(), bindingResult);
                yield FORM_VIEW;
            }
        };
    }

    /**
     * 提出の完了画面。URL のキーにも業務番号を使い、内部の ID を出さない（D-4）。
     * 他社の輸送要求の拒否（Q-INV-08）は、認証を入れる US-18・AC3 の Bolt で確かめる。
     */
    @GetMapping("/{number}/submitted")
    public String submitted(@PathVariable String number, Model model) {
        TransportRequest transportRequest = parse(number)
                .flatMap(queryService::findByNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute(
                "numberWithVersion",
                transportRequest.number().text() + " 版 "
                        + transportRequest.currentVersion().versionNo());
        model.addAttribute("statusLabel", statusLabel(transportRequest.status()));
        model.addAttribute(
                "submittedAt",
                DATE_TIME.format(ZonedDateTime.ofInstant(
                        transportRequest.currentVersion().submittedAt().instant(),
                        TransportRequestFormConverter.CUSTOMER_ZONE)));
        return "quotation/transport-requests/submitted";
    }

    private static Optional<TransportRequestNumber> parse(String number) {
        try {
            return Optional.of(TransportRequestNumber.parse(number));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    private static String statusLabel(TransportRequestStatus status) {
        return switch (status) {
            case UNDER_REVIEW -> "審査中";
        };
    }

    /**
     * 選択の欄の 1 つの選択肢。
     *
     * @param value 送る値（ドメインの定数名）
     * @param label 画面に出す名前
     */
    public record Option(String value, String label) {}
}
