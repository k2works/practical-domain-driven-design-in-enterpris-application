package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SendBackTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ReviewOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
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
 * 社内業務 Web の見積依頼の審査（S-02 受付一覧、S-03 審査。US-02、Bolt 5）。
 * 営業担当者はすべての荷主の見積依頼を扱うため、荷主企業で絞らない。認証（US-18）ができるまで、判断者は仮の営業担当者とする。
 */
@Controller
@RequestMapping("/staff/transport-requests")
public class TransportRequestReviewController {

    private static final String LIST_VIEW = "quotation/staff/transport-requests/list";
    private static final String REVIEW_VIEW = "quotation/staff/transport-requests/review";
    private static final String UNKNOWN_CONSIGNEE = "（仮の一覧にない荷受人）";

    /** 審査の入力の項目のキーから、エラー要約に出す表示名への対応。 */
    private static final Map<String, String> FIELD_LABELS =
            Map.of("rationale", "根拠", "reason", "理由", "missingItems", "不足事項");

    private final TransportRequestReviewService reviewService;
    private final TransportRequestQueryService queryService;
    private final ProvisionalActorProperties provisionalActor;
    private final ProvisionalConsigneeProperties provisionalConsignees;

    public TransportRequestReviewController(
            TransportRequestReviewService reviewService,
            TransportRequestQueryService queryService,
            ProvisionalActorProperties provisionalActor,
            ProvisionalConsigneeProperties provisionalConsignees) {
        this.reviewService = reviewService;
        this.queryService = queryService;
        this.provisionalActor = provisionalActor;
        this.provisionalConsignees = provisionalConsignees;
    }

    /** S-02 受付一覧。審査中の見積依頼を、提出時刻の古い順に示す。 */
    @GetMapping
    public String list(Model model) {
        model.addAttribute(
                "requests",
                queryService.findUnderReview().stream().map(this::row).toList());
        return LIST_VIEW;
    }

    /** S-03 審査。 */
    @GetMapping("/{number}")
    public String review(@PathVariable String number, Model model) {
        TransportRequest request = find(number);
        ReviewForm form = new ReviewForm();
        form.setVersionNo(request.currentVersion().versionNo());
        model.addAttribute("reviewForm", form);
        return showReview(request, model);
    }

    /**
     * 審査を確定する・差し戻す。受け付けたら受付一覧へ戻り、結果を示す（PRG。S-04 ができるまでの暫定の遷移）。
     * 受け付けなかったら、理由を示して審査画面を出し直す。
     */
    @PostMapping("/{number}/reviews")
    public String submitReview(
            @PathVariable String number,
            @ModelAttribute ReviewForm reviewForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        TransportRequestNumber transportRequestNumber =
                parse(number).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean approve = ReviewDecision.APPROVED.name().equals(reviewForm.getDecision());
        int versionNo = reviewForm.getVersionNo() == null ? 0 : reviewForm.getVersionNo();
        UserId reviewer = new UserId(provisionalActor.staffUserId());
        ReviewOutcome outcome = approve
                ? reviewService.approve(new ApproveTransportRequestCommand(
                        transportRequestNumber, versionNo, reviewer, reviewForm.getRationale()))
                : reviewService.sendBack(new SendBackTransportRequestCommand(
                        transportRequestNumber,
                        versionNo,
                        reviewer,
                        reviewForm.getReason(),
                        reviewForm.getMissingItems()));
        return switch (outcome) {
            case ReviewOutcome.Reviewed reviewed -> {
                redirectAttributes.addFlashAttribute("result", resultMessage(reviewed));
                yield "redirect:/staff/transport-requests";
            }
            case ReviewOutcome.Rejected(ReviewRejection reason, int currentVersionNo) -> {
                reject(reason, currentVersionNo, approve, reviewForm, bindingResult);
                yield showReview(find(number), model);
            }
            case ReviewOutcome.NotFound _ -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case ReviewOutcome.Conflict _ -> {
                bindingResult.reject("conflict", "他の利用者が先に更新しました。受付一覧から開き直してください");
                yield showReview(find(number), model);
            }
        };
    }

    private static void reject(
            ReviewRejection reason, int currentVersionNo, boolean approve, ReviewForm form, BindingResult errors) {
        String field = approve ? "rationale" : "reason";
        RejectionMessage message =
                switch (reason) {
                    case RATIONALE_REQUIRED ->
                        RejectionMessage.forField(field, approve ? "審査を確定する根拠を入力してください" : "差し戻す理由を入力してください");
                    case RATIONALE_TOO_LONG ->
                        RejectionMessage.forField(field, FIELD_LABELS.get(field) + "は 4,000 文字までで入力してください");
                    case MISSING_ITEMS_TOO_LONG ->
                        RejectionMessage.forField("missingItems", "不足事項は 4,000 文字までで入力してください");
                    case STALE_VERSION ->
                        RejectionMessage.global("この見積依頼は新しい版 " + currentVersionNo + " が出されています。最新の版を確認して審査し直してください");
                    case NOT_UNDER_REVIEW -> RejectionMessage.global("この見積依頼は審査中ではありません。受付一覧から確かめてください");
                };
        if (message.field() == null) {
            errors.reject(reason.name(), message.text());
        } else {
            errors.rejectValue(message.field(), reason.name(), message.text());
        }
        if (reason == ReviewRejection.STALE_VERSION) {
            form.setVersionNo(currentVersionNo);
        }
    }

    /**
     * 拒否の理由を示す場所と文言。
     *
     * @param field 誤りを付ける入力の項目（入力の誤りでない拒否は null）
     * @param text 文言
     */
    private record RejectionMessage(String field, String text) {

        static RejectionMessage forField(String field, String text) {
            return new RejectionMessage(field, text);
        }

        static RejectionMessage global(String text) {
            return new RejectionMessage(null, text);
        }
    }

    private String showReview(TransportRequest request, Model model) {
        ShipmentTerms terms = request.currentVersion().terms();
        Cargo cargo = terms.cargo();
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("荷受人", consigneeName(terms));
        rows.put("出発地", terms.origin().unLocode());
        rows.put("目的地", terms.destination().unLocode());
        rows.put("希望到着期限", TransportRequestLabels.staffDateTime(terms.arrivalDeadline()));
        rows.put("貨物種別", TransportRequestLabels.cargoCategory(cargo.category()));
        rows.put("荷姿", TransportRequestLabels.packageType(cargo.packageType()));
        rows.put("個数", String.valueOf(cargo.packageCount()));
        rows.put("総重量（kg）", cargo.grossWeightKg().stripTrailingZeros().toPlainString());
        rows.put("容積（m3）", cargo.volumeM3().stripTrailingZeros().toPlainString());
        rows.put(
                "提出時刻",
                TransportRequestLabels.staffDateTime(request.currentVersion().submittedAt()));
        model.addAttribute("numberWithVersion", numberWithVersion(request));
        model.addAttribute("number", request.number().text());
        model.addAttribute("statusLabel", TransportRequestLabels.status(request.status()));
        model.addAttribute("terms", rows);
        model.addAttribute("fieldLabels", FIELD_LABELS);
        return REVIEW_VIEW;
    }

    private Row row(TransportRequest request) {
        ShipmentTerms terms = request.currentVersion().terms();
        return new Row(
                request.number().text(),
                request.currentVersion().versionNo(),
                TransportRequestLabels.staffDateTime(request.currentVersion().submittedAt()),
                terms.origin().unLocode() + " → " + terms.destination().unLocode(),
                TransportRequestLabels.staffDateTime(terms.arrivalDeadline()));
    }

    private String consigneeName(ShipmentTerms terms) {
        return provisionalConsignees
                .find(terms.consigneeCompanyId().value())
                .map(ProvisionalConsigneeProperties.Company::name)
                .orElse(UNKNOWN_CONSIGNEE);
    }

    private TransportRequest find(String number) {
        return parse(number)
                .flatMap(queryService::findByNumberForStaff)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static String numberWithVersion(TransportRequest request) {
        return request.number().text() + " 版 " + request.currentVersion().versionNo();
    }

    private static String resultMessage(ReviewOutcome.Reviewed reviewed) {
        String target = reviewed.number().text() + " 版 " + reviewed.versionNo();
        return reviewed.decision() == ReviewDecision.APPROVED ? target + " の審査を確定しました" : target + " を差し戻しました";
    }

    private static Optional<TransportRequestNumber> parse(String number) {
        try {
            return Optional.of(TransportRequestNumber.parse(number));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    /**
     * 受付一覧の 1 行。
     *
     * @param number 業務番号
     * @param versionNo 版番号
     * @param submittedAt 提出時刻（社内の日時表示）
     * @param route 出発地 → 目的地
     * @param arrivalDeadline 希望到着期限（社内の日時表示）
     */
    public record Row(String number, int versionNo, String submittedAt, String route, String arrivalDeadline) {}
}
