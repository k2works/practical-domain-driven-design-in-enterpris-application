package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SendBackTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ReviewOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.entities.ReviewRecord;
import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Duration;
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
    private final StaffTransportRequestQueryService queryService;
    private final ProvisionalActorProperties provisionalActor;
    private final ProvisionalConsigneeProperties provisionalConsignees;
    private final Clock clock;

    public TransportRequestReviewController(
            TransportRequestReviewService reviewService,
            StaffTransportRequestQueryService queryService,
            ProvisionalActorProperties provisionalActor,
            ProvisionalConsigneeProperties provisionalConsignees,
            Clock clock) {
        this.reviewService = reviewService;
        this.queryService = queryService;
        this.provisionalActor = provisionalActor;
        this.provisionalConsignees = provisionalConsignees;
        this.clock = clock;
    }

    /** S-02 受付一覧。審査中の見積依頼を、最初の提出時刻の古い順（待たせている順）に示す。 */
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
     * 受け付けなかったら、理由を示して審査画面を出し直す。判断と版番号が送られていなければ、要求そのものの誤り（400）とする。
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
        ReviewDecision decision = decision(reviewForm.getDecision());
        if (reviewForm.getVersionNo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "審査の対象の版番号がありません");
        }
        int versionNo = reviewForm.getVersionNo();
        UserId reviewer = new UserId(provisionalActor.staffUserId());
        ReviewOutcome outcome =
                switch (decision) {
                    case APPROVED ->
                        reviewService.approve(new ApproveTransportRequestCommand(
                                transportRequestNumber, versionNo, reviewer, reviewForm.getRationale()));
                    case SENT_BACK ->
                        reviewService.sendBack(new SendBackTransportRequestCommand(
                                transportRequestNumber,
                                versionNo,
                                reviewer,
                                reviewForm.getReason(),
                                reviewForm.getMissingItems()));
                };
        return switch (outcome) {
            case ReviewOutcome.Reviewed reviewed -> {
                redirectAttributes.addFlashAttribute("result", resultMessage(reviewed));
                yield "redirect:/staff/transport-requests";
            }
            case ReviewOutcome.Rejected(ReviewRejection reason, int currentVersionNo) -> {
                reject(reason, currentVersionNo, decision, reviewForm, bindingResult);
                yield showReview(find(number), model);
            }
            case ReviewOutcome.NotFound _ -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case ReviewOutcome.Conflict _ -> {
                bindingResult.reject("conflict", "他の利用者が先に更新しました。受付一覧から開き直してください");
                yield showReview(find(number), model);
            }
        };
    }

    /** 判断の値を解釈する。知らない値やないときは、取り違えを防ぐため要求そのものの誤り（400）とする（Bolt 5 レビュー R-01）。 */
    private static ReviewDecision decision(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "審査の判断がありません");
        }
        try {
            return ReviewDecision.valueOf(value);
        } catch (IllegalArgumentException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "審査の判断の値が正しくありません");
        }
    }

    private static void reject(
            ReviewRejection reason,
            int currentVersionNo,
            ReviewDecision decision,
            ReviewForm form,
            BindingResult errors) {
        String field = decision == ReviewDecision.APPROVED ? "rationale" : "reason";
        RejectionMessage message =
                switch (reason) {
                    case RATIONALE_REQUIRED ->
                        RejectionMessage.forField(
                                field, decision == ReviewDecision.APPROVED ? "審査を確定する根拠を入力してください" : "差し戻す理由を入力してください");
                    case RATIONALE_TOO_LONG ->
                        RejectionMessage.forField(field, FIELD_LABELS.get(field) + "は 4,000 文字までで入力してください");
                    case MISSING_ITEMS_TOO_LONG ->
                        RejectionMessage.forField("missingItems", "不足事項は 4,000 文字までで入力してください");
                    case STALE_VERSION ->
                        RejectionMessage.global("この見積依頼は新しい版 " + currentVersionNo + " が出されています。版 " + currentVersionNo
                                + " の内容を確かめてから、根拠・理由を書き直してください");
                    case NOT_UNDER_REVIEW -> RejectionMessage.global("この見積依頼は審査中ではありません。受付一覧から確かめてください");
                };
        if (message.field() == null) {
            errors.reject(reason.name(), message.text());
        } else {
            errors.rejectValue(message.field(), reason.name(), message.text());
        }
        if (reason == ReviewRejection.STALE_VERSION) {
            // 古い版に向けて書いた根拠・理由のまま、新しい版を確かめずに確定させない（Bolt 5 レビュー R-11）
            form.setVersionNo(currentVersionNo);
            form.setRationale(null);
            form.setReason(null);
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
        model.addAttribute(
                "numberWithVersion",
                TransportRequestLabels.numberWithVersion(
                        request.number(), request.currentVersion().versionNo()));
        model.addAttribute("number", request.number().text());
        model.addAttribute("statusLabel", TransportRequestLabels.status(request.status()));
        model.addAttribute("terms", rows);
        model.addAttribute(
                "history",
                request.reviewRecords().stream()
                        .map(TransportRequestReviewController::history)
                        .toList());
        model.addAttribute("fieldLabels", FIELD_LABELS);
        return REVIEW_VIEW;
    }

    private Row row(TransportRequestSummary summary) {
        return new Row(
                summary.number().text(),
                summary.versionNo(),
                TransportRequestLabels.staffDateTime(summary.firstSubmittedAt()),
                TransportRequestLabels.elapsed(
                        Duration.between(summary.firstSubmittedAt().instant(), clock.instant())),
                summary.origin().unLocode() + " → " + summary.destination().unLocode(),
                TransportRequestLabels.staffDateTime(summary.arrivalDeadline()),
                TransportRequestLabels.cargoCategory(summary.cargoCategory()));
    }

    private static History history(ReviewRecord reviewRecord) {
        return new History(
                "版 " + reviewRecord.versionNo(),
                TransportRequestLabels.decision(reviewRecord.decision()),
                reviewRecord.rationale(),
                reviewRecord.missingItems() == null ? "（なし）" : reviewRecord.missingItems(),
                TransportRequestLabels.staffDateTime(reviewRecord.decidedAt()));
    }

    private String consigneeName(ShipmentTerms terms) {
        return provisionalConsignees
                .find(terms.consigneeCompanyId().value())
                .map(ProvisionalConsigneeProperties.Company::name)
                .orElse(UNKNOWN_CONSIGNEE);
    }

    private TransportRequest find(String number) {
        return parse(number)
                .flatMap(queryService::findByNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static String resultMessage(ReviewOutcome.Reviewed reviewed) {
        String target = TransportRequestLabels.numberWithVersion(reviewed.number(), reviewed.versionNo());
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

    /**
     * 受付一覧の 1 行。
     *
     * @param number 業務番号
     * @param versionNo 版番号
     * @param firstSubmittedAt 最初の提出時刻（社内の日時表示）
     * @param waiting 最初の提出から待っている時間
     * @param route 出発地 → 目的地
     * @param arrivalDeadline 希望到着期限（社内の日時表示）
     * @param cargoCategory 貨物種別
     */
    public record Row(
            String number,
            int versionNo,
            String firstSubmittedAt,
            String waiting,
            String route,
            String arrivalDeadline,
            String cargoCategory) {}

    /**
     * 審査画面の審査記録の 1 行（これまでの判断）。
     *
     * @param version 対象の版
     * @param decision 判断
     * @param rationale 根拠（確定）または理由（差戻し）
     * @param missingItems 不足事項
     * @param decidedAt 判断時刻（社内の日時表示）
     */
    public record History(String version, String decision, String rationale, String missingItems, String decidedAt) {}
}
