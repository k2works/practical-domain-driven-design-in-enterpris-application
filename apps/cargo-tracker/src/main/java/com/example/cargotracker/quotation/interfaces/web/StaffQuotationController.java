package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequoteQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.CalculationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.PresentationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.RequotationOutcome;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestLookup;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.rules.RequotePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
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
 * 算出した見積りを確かめて社内承認して提示する。営業担当者が扱えるのは担当企業の見積依頼だけで（Bolt 38f。US-16 AC4）、範囲の外は
 * 見つからない扱い（404）にし、確定の試行（POST）だけをアクセス・監査の記録に残す（SEC-12。{@link StaffOutOfScopeResponder}）。
 * 社内承認者はログインした営業担当者（認証の主体。ADR-012）。
 * 承認待ち・提示済みの見積りからは再見積りでき、旧版は置換済み（有効期限を過ぎていれば失効）として読み取り専用で残る（Bolt 11）。
 */
@Controller
@RequestMapping("/staff/transport-requests/{number}/quotations")
public class StaffQuotationController {

    private static final String LIST_PATH = "/staff/transport-requests";
    private static final String REDIRECT = "redirect:";
    private static final String FORM_VIEW = "quotation/staff/quotations/new";
    private static final String SHOW_VIEW = "quotation/staff/quotations/show";
    private static final String RESULT = "result";
    private static final String PROBLEM = "problem";
    private static final String CONFLICT_MESSAGE = "他の利用者が先に更新しました。内容を確かめてください";
    private static final String FORM_HEADING = "formHeading";
    private static final String FORM_ACTION = "formAction";
    private static final String INHERITED_ROUTE = "承認済みの経路版と経路方針を引き継いだ見積りです";

    private final QuotationCommandService commandService;
    private final StaffQuotationQueryService queryService;
    private final StaffTransportRequestQueryService transportRequestQueryService;
    private final ProvisionalConsigneeProperties provisionalConsignees;
    private final StaffOutOfScopeResponder outOfScope;
    private final Clock clock;

    public StaffQuotationController(
            QuotationCommandService commandService,
            StaffQuotationQueryService queryService,
            StaffTransportRequestQueryService transportRequestQueryService,
            ProvisionalConsigneeProperties provisionalConsignees,
            StaffOutOfScopeResponder outOfScope,
            Clock clock) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.transportRequestQueryService = transportRequestQueryService;
        this.provisionalConsignees = provisionalConsignees;
        this.outOfScope = outOfScope;
        this.clock = clock;
    }

    /**
     * S-04 見積りの作成。見積依頼に作成中・承認待ち・提示済みの見積りがあれば、作成画面でなくその見積りへ移す
     * （算出した後に画面を離れても、受付一覧の見積り作成中から戻れる。Bolt 9・10 レビュー R-04）。
     */
    @GetMapping("/new")
    public String newQuotation(@PathVariable String number, AuthenticatedActor actor, Model model) {
        TransportRequest request = findTransportRequest(number, actor);
        Optional<Quotation> active = queryService.findActive(request.number(), actor);
        if (active.isPresent()) {
            return REDIRECT + quotationPath(request.number(), active.get().quotationNo());
        }
        model.addAttribute("quotationForm", new QuotationForm());
        return showCreateForm(request, model);
    }

    /** 見積りを作って算出する。算出したら算出した見積りの画面へ移り（PRG）、誤りがあれば入力を残してエラー要約で示す（AC3）。 */
    @PostMapping
    public String calculate(
            @PathVariable String number,
            AuthenticatedActor actor,
            @ModelAttribute QuotationForm quotationForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequest request = confirmingTransportRequest(number, actor, null);
        List<Integer> rowsOfLines = new ArrayList<>();
        Optional<QuotationInput> input = QuotationFormConverter.convert(quotationForm, bindingResult, rowsOfLines);
        if (input.isEmpty()) {
            return showCreateForm(request, model);
        }
        return switch (commandService.calculate(new CalculateQuotationCommand(request.number(), input.get()), actor)) {
            case CalculationOutcome.Calculated(TransportRequestNumber calculated, int quotationNo) -> {
                redirectAttributes.addFlashAttribute(
                        RESULT, QuotationViews.label(calculated, quotationNo) + " を算出しました。内容を確かめて社内承認してください");
                yield REDIRECT + quotationPath(calculated, quotationNo);
            }
            case CalculationOutcome.Invalid(QuotationViolations violations) -> {
                QuotationViolationMessages.reject(violations, bindingResult, rowsOfLines);
                yield showCreateForm(request, model);
            }
            case CalculationOutcome.Rejected(QuotationRejection reason) -> {
                Optional<Quotation> active = queryService.findActive(request.number(), actor);
                if (reason == QuotationRejection.ALREADY_QUOTED && active.isPresent()) {
                    redirectAttributes.addFlashAttribute(
                            PROBLEM, request.number().text() + " にはすでに見積りがあります。その見積りを示します");
                    yield REDIRECT
                            + quotationPath(request.number(), active.get().quotationNo());
                }
                redirectAttributes.addFlashAttribute(
                        PROBLEM, rejection(request.number().text(), reason));
                yield REDIRECT + LIST_PATH;
            }
            case CalculationOutcome.NotFound _ -> throw notFound();
            case CalculationOutcome.OutOfScope _ ->
                throw outOfScope.recordAndNotFound(
                        actor,
                        ScopedResourceKind.TRANSPORT_REQUEST,
                        request.number().text());
        };
    }

    /**
     * 算出した見積り。承認待ちで失効していなければ社内承認して提示するボタンを、承認待ち・提示済みなら再見積りの操作を出す。
     * 失効（表示する時刻で有効期限を過ぎた）と置換済みは読み取り専用で、置換済みなら置換先へのリンクを示す（Bolt 11）。
     */
    @GetMapping("/{quotationNo}")
    public String show(
            @PathVariable String number, @PathVariable int quotationNo, AuthenticatedActor actor, Model model) {
        TransportRequest request = findTransportRequest(number, actor);
        Quotation quotation =
                queryService.find(request.number(), quotationNo, actor).orElseThrow(StaffQuotationController::notFound);
        UtcInstant now = new UtcInstant(clock.instant());
        // 再見積りの操作の出し分けは、GET・POST と同じ規則を使う（輸送要求の状態と版を含む。D-85。Bolt 35c）
        Optional<QuotationRejection> requoteRejection = RequotePolicy.rejection(request, quotation, now);
        model.addAttribute("requotable", requoteRejection.isEmpty() && quotation.status() != QuotationStatus.DRAFT);
        model.addAttribute(
                "expiredNotice",
                expiredNotice(quotation, now, QuotationViews.label(request.number(), quotationNo), requoteRejection));
        model.addAttribute("routingRequestedNotice", routingRequestedNotice(quotation, now));
        List<Quotation> all =
                quotation.status() == QuotationStatus.REPLACED || quotation.status() == QuotationStatus.EXPIRED
                        ? queryService.findAll(request.number(), actor)
                        : List.of();
        model.addAttribute(
                "replacement",
                quotation
                        .replacedBy()
                        .flatMap(replacement -> all.stream()
                                .filter(candidate -> candidate.id().equals(replacement))
                                .findFirst())
                        .map(found -> link(request.number(), found.quotationNo()))
                        .orElse(null));
        // 失効を記録した旧版は置換先を持たないため、最新の見積りへのリンクを出す（Bolt 11 レビュー R-09）
        model.addAttribute(
                "latest",
                quotation.status() == QuotationStatus.EXPIRED
                        ? all.stream()
                                .max(Comparator.comparingInt(Quotation::quotationNo))
                                .filter(latest -> latest.quotationNo() != quotationNo)
                                .map(latest -> link(request.number(), latest.quotationNo()))
                                .orElse(null)
                        : null);
        model.addAttribute("number", request.number().text());
        model.addAttribute("quotationLabel", QuotationViews.label(request.number(), quotationNo));
        model.addAttribute(
                "numberWithVersion",
                TransportRequestLabels.numberWithVersion(request.number(), quotation.transportRequestVersionNo()));
        model.addAttribute(
                "quotation",
                QuotationViews.view(
                        quotation, TransportRequestLabels::staffDateTime, now, QuotationViews.Audience.STAFF));
        return SHOW_VIEW;
    }

    /**
     * S-04 の再見積り。旧版の料金明細・通貨・経路方針を初期値にし、有効期限は空にする。再見積りできない見積り（置換済み・失効の記録、
     * 期限内の承認済み、予約確定済みの見積依頼など）からは開けない（判定は POST と同じ規則。D-85。Bolt 35c）。経路を持つ旧版からは、
     * 引き継ぐ経路方針と経路版を読み取り専用で示す。
     */
    @GetMapping("/{quotationNo}/requotation")
    public String requotation(
            @PathVariable String number,
            @PathVariable int quotationNo,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequest request = findTransportRequest(number, actor);
        Quotation quotation =
                queryService.find(request.number(), quotationNo, actor).orElseThrow(StaffQuotationController::notFound);
        Optional<QuotationRejection> rejection =
                RequotePolicy.rejection(request, quotation, new UtcInstant(clock.instant()));
        if (rejection.isPresent()) {
            redirectAttributes.addFlashAttribute(
                    PROBLEM, rejection(QuotationViews.label(request.number(), quotationNo), rejection.get()));
            return REDIRECT + quotationPath(request.number(), quotationNo);
        }
        model.addAttribute("quotationForm", QuotationFormConverter.toForm(quotation));
        return showRequotationForm(request, quotationNo, Optional.of(quotation), model);
    }

    /**
     * 再見積りする。算出したら新しい見積りへ移り（PRG）、誤りがあれば入力を残してエラー要約で示す。
     * 受け付けなかったとき（置換済み・失効など）と競合したときは、旧版に戻して理由を示す。経路を持つ旧版からの再見積りでは、経路方針を
     * 画面から受けず、旧版の値を入力に入れる（送られた値は捨てる。D-85。Bolt 35c）。ここでの上書きは入力の検証（出発・到着は必須）を
     * 通すためで、引き継ぐ値の正は集約の {@code requoteInto} の上書き。
     */
    @PostMapping("/{quotationNo}/requotation")
    public String requote(
            @PathVariable String number,
            @PathVariable int quotationNo,
            AuthenticatedActor actor,
            @ModelAttribute QuotationForm quotationForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequest request = confirmingTransportRequest(number, actor, quotationNo);
        Optional<Quotation> old = queryService.find(request.number(), quotationNo, actor);
        old.filter(quotation -> quotation.assignedRoute().isPresent())
                .map(QuotationFormConverter::toForm)
                .ifPresent(inherited -> {
                    quotationForm.setVia(inherited.getVia());
                    quotationForm.setDepartureAt(inherited.getDepartureAt());
                    quotationForm.setArrivalAt(inherited.getArrivalAt());
                });
        List<Integer> rowsOfLines = new ArrayList<>();
        Optional<QuotationInput> input = QuotationFormConverter.convert(quotationForm, bindingResult, rowsOfLines);
        if (input.isEmpty()) {
            return showRequotationForm(request, quotationNo, old, model);
        }
        String oldLabel = QuotationViews.label(request.number(), quotationNo);
        return switch (commandService.requote(
                new RequoteQuotationCommand(request.number(), quotationNo, input.get()), actor)) {
            case RequotationOutcome.Calculated(
                    TransportRequestNumber calculated,
                    int newNo,
                    QuotationStatus previousStatus) -> {
                String previous = previousStatus == QuotationStatus.EXPIRED ? "は失効として記録しました" : "は新しい見積りに置き換えました";
                redirectAttributes.addFlashAttribute(
                        RESULT,
                        QuotationViews.label(calculated, newNo) + " を算出しました。見積 " + quotationNo + " " + previous
                                + "。内容を確かめて社内承認してください");
                yield REDIRECT + quotationPath(calculated, newNo);
            }
            case RequotationOutcome.Invalid(QuotationViolations violations) -> {
                QuotationViolationMessages.reject(violations, bindingResult, rowsOfLines);
                yield showRequotationForm(request, quotationNo, old, model);
            }
            case RequotationOutcome.Rejected(QuotationRejection reason) -> {
                redirectAttributes.addFlashAttribute(PROBLEM, rejection(oldLabel, reason));
                yield REDIRECT + quotationPath(request.number(), quotationNo);
            }
            case RequotationOutcome.Conflict _ -> {
                redirectAttributes.addFlashAttribute(PROBLEM, CONFLICT_MESSAGE);
                yield REDIRECT + quotationPath(request.number(), quotationNo);
            }
            case RequotationOutcome.NotFound _ -> throw notFound();
            case RequotationOutcome.OutOfScope _ ->
                throw outOfScope.recordAndNotFound(
                        actor, ScopedResourceKind.QUOTATION, quotationId(request.number(), quotationNo));
        };
    }

    /** 社内承認して提示する。提示したら受付一覧へ戻り、結果を示す（PRG）。 */
    @PostMapping("/{quotationNo}/presentation")
    public String present(
            @PathVariable String number,
            @PathVariable int quotationNo,
            AuthenticatedActor actor,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber transportRequestNumber =
                confirmingTransportRequest(number, actor, quotationNo).number();
        UserId approver = actor.userId();
        return switch (commandService.present(
                new PresentQuotationCommand(transportRequestNumber, quotationNo, approver), actor)) {
            case PresentationOutcome.Presented(TransportRequestNumber presented, int presentedNo) -> {
                redirectAttributes.addFlashAttribute(RESULT, QuotationViews.label(presented, presentedNo) + " を提示しました");
                yield REDIRECT + LIST_PATH;
            }
            case PresentationOutcome.Rejected(QuotationRejection reason) -> {
                redirectAttributes.addFlashAttribute(
                        PROBLEM, rejection(QuotationViews.label(transportRequestNumber, quotationNo), reason));
                yield REDIRECT + quotationPath(transportRequestNumber, quotationNo);
            }
            case PresentationOutcome.Conflict _ -> {
                redirectAttributes.addFlashAttribute(PROBLEM, CONFLICT_MESSAGE);
                yield REDIRECT + quotationPath(transportRequestNumber, quotationNo);
            }
            case PresentationOutcome.NotFound _ -> throw notFound();
            case PresentationOutcome.OutOfScope _ ->
                throw outOfScope.recordAndNotFound(
                        actor, ScopedResourceKind.QUOTATION, quotationId(transportRequestNumber, quotationNo));
        };
    }

    private String showCreateForm(TransportRequest request, Model model) {
        model.addAttribute(FORM_HEADING, "見積りの作成");
        model.addAttribute(FORM_ACTION, LIST_PATH + "/" + request.number().text() + "/quotations");
        return showForm(request, model);
    }

    private String showRequotationForm(
            TransportRequest request, int quotationNo, Optional<Quotation> old, Model model) {
        model.addAttribute(FORM_HEADING, "再見積り（" + QuotationViews.label(request.number(), quotationNo) + " から）");
        model.addAttribute(FORM_ACTION, quotationPath(request.number(), quotationNo) + "/requotation");
        Optional<Quotation> inheriting =
                old.filter(quotation -> quotation.assignedRoute().isPresent());
        String replacing = "算出すると、見積 " + quotationNo + " を新しい見積りに置き換えます（有効期限を過ぎていれば失効として記録します）。";
        model.addAttribute(
                "requotationNotice",
                inheriting.isPresent()
                        ? "承認済みの経路版と経路方針を引き継ぎ、料金と有効期限だけを出し直します。" + replacing + "提示すると荷主の再承認を待ちます。"
                        : replacing + "旧版は読み取り専用で残ります。");
        // 引き継ぐ経路方針と経路版は、入力欄でなく読み取り専用で示す（D-85。Bolt 35c）
        inheriting.ifPresent(quotation -> {
            QuotationViews.View view = QuotationViews.view(
                    quotation,
                    TransportRequestLabels::staffDateTime,
                    new UtcInstant(clock.instant()),
                    QuotationViews.Audience.STAFF);
            model.addAttribute("inheritedQuotation", view);
            model.addAttribute("inheritedRoute", view.route());
        });
        return showForm(request, model);
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

    /** 照会（GET）の見積依頼を担当範囲の中で引き当てる。見つからなくても範囲の外でも 404（照会は記録しない。Bolt 38f）。 */
    private TransportRequest findTransportRequest(String number, AuthenticatedActor actor) {
        return TransportRequestViews.parseNumber(number)
                .flatMap(parsed ->
                        transportRequestQueryService.findByNumber(parsed, actor).inScope())
                .orElseThrow(StaffQuotationController::notFound);
    }

    /**
     * 確定（POST）の見積依頼を担当範囲の中で引き当てる。範囲の外なら入力の解釈より先に試行を記録して 404 にする（Bolt 38f、SEC-12）。
     *
     * @param quotationNo 見積りへの操作なら見積り番号（記録の対象を「業務番号/見積り番号」にする）。見積依頼への操作なら null
     */
    private TransportRequest confirmingTransportRequest(String number, AuthenticatedActor actor, Integer quotationNo) {
        TransportRequestNumber parsed =
                TransportRequestViews.parseNumber(number).orElseThrow(StaffQuotationController::notFound);
        StaffTransportRequestLookup lookup = transportRequestQueryService.findByNumber(parsed, actor);
        // 範囲の外の確かめは審査の controller と同じ書き方（範囲の外なら記録して 404、そうでなければ範囲の中を引き当てる。M2）
        if (lookup instanceof StaffTransportRequestLookup.OutOfScope) {
            throw quotationNo == null
                    ? outOfScope.recordAndNotFound(actor, ScopedResourceKind.TRANSPORT_REQUEST, parsed.text())
                    : outOfScope.recordAndNotFound(
                            actor, ScopedResourceKind.QUOTATION, quotationId(parsed, quotationNo));
        }
        return lookup.inScope().orElseThrow(StaffQuotationController::notFound);
    }

    /** 記録の対象の見積りの識別（業務番号/見積り番号）。 */
    private static String quotationId(TransportRequestNumber number, int quotationNo) {
        return number.text() + "/" + quotationNo;
    }

    private static String quotationPath(TransportRequestNumber number, int quotationNo) {
        return LIST_PATH + "/" + number.text() + "/quotations/" + quotationNo;
    }

    /**
     * 見積りの操作を受け付けなかった理由の文言（Bolt 9・10 レビュー R-16）。算出は見積依頼、提示は見積りを主語にする。
     *
     * @param subject 主語（業務番号、または「TR-2026-0001 見積 1」）
     */
    private static String rejection(String subject, QuotationRejection reason) {
        return switch (reason) {
            case TRANSPORT_REQUEST_NOT_QUOTING, TRANSPORT_REQUEST_BOOKED -> transportRequestRejection(subject, reason);
            case ALREADY_QUOTED -> subject + " にはすでに見積りがあります";
            case NOT_PENDING_APPROVAL -> subject + " は承認待ちでないため提示できません";
            case EXPIRED -> subject + " は有効期限を過ぎて失効しています。再見積りしてください";
            case REPLACED -> subject + " は置換済みです。新しい見積りを使ってください";
            case OUTDATED_VERSION -> subject + " は輸送要求の古い版に対する見積りのため、再見積りできません";
            case NOT_PRESENTED -> subject + " は荷主に提示していません";
            case ROUTING_REQUESTED -> subject + " は荷主が詳細経路設計を依頼済みのため、再見積りできません";
            // ROUTE_REDESIGN_REQUIRED・ROUTE_ALREADY_DEPARTED は荷主の承認と予約確定の照会と再見積りで返る（言い分けは
            // approvalStageRejection。Bolt 37e3・37e4）
            case NOT_AWAITING_SHIPPER_APPROVAL,
                    ALREADY_APPROVED,
                    NOT_APPROVED,
                    APPROVED_AND_STILL_VALID,
                    ROUTE_REDESIGN_REQUIRED,
                    ROUTE_ALREADY_DEPARTED -> approvalStageRejection(subject, reason);
        };
    }

    /** 見積依頼の状態で見積りの操作を受け付けなかった理由の文言（予約確定済みは D-85。Bolt 35c）。 */
    private static String transportRequestRejection(String subject, QuotationRejection reason) {
        if (reason == QuotationRejection.TRANSPORT_REQUEST_BOOKED) {
            return subject + " は本予約を確定済みのため、再見積りできません";
        }
        return subject + " は見積り作成中でないため、見積りを作れません";
    }

    /** 荷主の承認の段階にある見積りの操作を受け付けなかった理由の文言。 */
    private static String approvalStageRejection(String subject, QuotationRejection reason) {
        // 期限内の承認済みの見積りの再見積り（D-85。UI 設計の S-04。Bolt 35c）
        if (reason == QuotationRejection.APPROVED_AND_STILL_VALID) {
            return subject + " は荷主が承認済みで、まだ有効期限内です。本予約の確定へ進んでください";
        }
        // 割り当てた経路の最初の区間が出発済みの承認済みの期限内の再見積り（D-88。Bolt 37e4）
        if (reason == QuotationRejection.ROUTE_ALREADY_DEPARTED) {
            return subject + " は割り当てた経路の最初の区間が出発済みのため、有効期限まで再見積りできません。荷主と扱いを相談してください";
        }
        // 割り当てた経路が再設計要の見積りの期限内の再見積り（US-07 AC5。Bolt 37e3）
        if (reason == QuotationRejection.ROUTE_REDESIGN_REQUIRED) {
            return subject + " は割り当てた経路が再設計要のため、有効期限まで再見積りできません。経路設計者と扱いを相談してください";
        }
        // NOT_APPROVED は予約確定の照会の理由で、この画面の操作では返らない（Bolt 23）
        return subject + " は荷主の承認の段階にあるため、この操作はできません";
    }

    /**
     * 期限の後に再見積りできるが、新しい見積りも経路を引き継いで荷主が承認できない見積りの案内。割り当てた経路が再設計要（US-07 AC5。
     * Bolt 37e3）か、承認済みで最初の区間が出発済み（D-88。Bolt 37e4）。どちらでもなければ null。
     */
    private static String routeNoticeAfterExpiry(Quotation quotation, UtcInstant now, String expiresAt) {
        if (quotation.isRouteRedesignRequired()) {
            return expiresAt + "を過ぎました。割り当てた経路が再設計要のため、経路を引き継いで再見積りしても荷主は承認できません。経路設計者と扱いを相談してから再見積りしてください。";
        }
        if (quotation.status() == QuotationStatus.APPROVED && quotation.isRouteDepartedAt(now)) {
            return expiresAt + "を過ぎました。割り当てた経路の最初の区間が出発済みのため、経路を引き継いで再見積りしても荷主は承認できません。荷主と扱いを相談してから再見積りしてください。";
        }
        return null;
    }

    /**
     * 失効しているときの案内。有効期限の日時を併記する（Bolt 11 レビュー R-17）。失効を記録した旧版は読み取り専用、
     * 表示する時刻で失効した承認待ちは提示できず、提示済みは使えない。荷主の承認の後に失効した見積りは、再見積りできれば経路を引き継ぐ
     * 再見積りを案内し、できなければその理由を示す（D-85。Bolt 35c）。失効していなければ null。
     */
    private static String expiredNotice(
            Quotation quotation, UtcInstant now, String label, Optional<QuotationRejection> requoteRejection) {
        if (!quotation.isExpiredAt(now)) {
            return null;
        }
        String expiresAt = "有効期限（"
                + TransportRequestLabels.staffDateTime(
                        quotation.expiry().orElseThrow().expiresAt())
                + "）";
        if (quotation.status() == QuotationStatus.EXPIRED) {
            return expiresAt + "を過ぎて失効しました。この見積りは読み取り専用です。";
        }
        if (requoteRejection.isEmpty()) {
            String routeNotice = routeNoticeAfterExpiry(quotation, now, expiresAt);
            if (routeNotice != null) {
                return routeNotice;
            }
        }
        if (quotation.status() == QuotationStatus.APPROVED) {
            return requoteRejection
                    .map(reason -> "荷主の承認の後に" + expiresAt + "を過ぎました。" + rejection(label, reason) + "。")
                    .orElse("荷主の承認の後に" + expiresAt + "を過ぎました。承認済みの経路版と経路方針を引き継いで再見積りできます。荷主の再承認が要ります。");
        }
        if (quotation.status().isRoutingStarted()) {
            return expiresAt + "を過ぎたため、この見積りは使えません。荷主が詳細経路設計を依頼済みのため、再見積りはできません。"
                    + "荷主と経路設計者に連絡し、扱いを決めてください（経路設計の途中の再見積りは今後の更新で入れます）。";
        }
        String action = quotation.status() == QuotationStatus.PENDING_APPROVAL ? "提示できません" : "使えません";
        return expiresAt + "を過ぎたため、この見積りは" + action + "。再見積りしてください。";
    }

    /**
     * 荷主が詳細経路設計を依頼した見積りの案内（Bolt 12）。依頼した日時を示し、読み取り専用とする。失効していれば失効の案内に任せて null。
     * 依頼者の名前は、利用者の管理（US-16・US-18）ができるまで出さない。
     */
    private static String routingRequestedNotice(Quotation quotation, UtcInstant now) {
        if (!quotation.status().isRoutingStarted() || quotation.isExpiredAt(now)) {
            return null;
        }
        if (quotation.isPresentedForReapproval()) {
            // 再承認の見積りは回答時刻を引き継いでいるので、依頼の文でなく再承認を示す（D-85。Bolt 35d）
            return quotation
                            .shipperApproval()
                            .map(approval ->
                                    "荷主が " + TransportRequestLabels.staffDateTime(approval.approvedAt()) + "に再承認しました")
                            .orElse("荷主の再承認を待っています")
                    + "（" + INHERITED_ROUTE + "）。この見積りは読み取り専用です。";
        }
        return "荷主が "
                + TransportRequestLabels.staffDateTime(quotation.respondedAt().orElseThrow())
                + "に詳細経路設計を依頼しました。この見積りは読み取り専用です。";
    }

    private static QuotationLink link(TransportRequestNumber number, int quotationNo) {
        return new QuotationLink(
                quotationNo, QuotationViews.label(number, quotationNo), quotationPath(number, quotationNo));
    }

    /**
     * ほかの見積り（置換先・最新の見積り）へのリンク。
     *
     * @param quotationNo 見積り番号
     * @param label 表記（例: TR-2026-0001 見積 2）
     * @param path 画面のパス
     */
    record QuotationLink(int quotationNo, String label, String path) {}

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
