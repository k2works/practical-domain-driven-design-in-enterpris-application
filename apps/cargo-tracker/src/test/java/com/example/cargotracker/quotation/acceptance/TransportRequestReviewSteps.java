package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SendBackTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ResubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.ReviewOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.entities.ReviewRecord;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 審査（US-02）のステップ定義。見積りの入力ポートだけを呼ぶ（AT-05）。
 * 審査の対象は、同じシナリオで提出した輸送要求（ScenarioContext の輸送要求 ID）とする。
 */
public class TransportRequestReviewSteps {

    /** 認証ができるまでの仮の営業担当者（Bolt 5 の人の決定）。 */
    private static final UserId REVIEWER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301"));

    /** 提出したのと同じ荷主（TransportRequestSteps と同じ値）。 */
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static final UserId SUBMITTER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

    private static final Map<String, ReviewRejection> REJECTIONS = Map.of(
            "根拠がない", ReviewRejection.RATIONALE_REQUIRED,
            "古い版", ReviewRejection.STALE_VERSION,
            "審査中でない", ReviewRejection.NOT_UNDER_REVIEW);

    private static final Map<String, TransportRequestStatus> STATUSES = Map.of(
            "下書き", TransportRequestStatus.DRAFT,
            "審査中", TransportRequestStatus.UNDER_REVIEW,
            "見積り作成中", TransportRequestStatus.QUOTING);

    /** 再提出の検証の誤りの呼び名（このシナリオで使うものだけ。提出の誤りは TransportRequestSteps で網羅する）。 */
    private static final Map<String, Item> ITEMS = Map.of("目的地", Item.DESTINATION);

    private static final Map<String, Reason> REASONS = Map.of("出発地と同じ", Reason.SAME_AS_ORIGIN);

    private static final Map<String, ReviewDecision> DECISIONS =
            Map.of("充足", ReviewDecision.APPROVED, "差戻し", ReviewDecision.SENT_BACK);

    private final TransportRequestReviewService reviewService;
    private final TransportRequestCommandService commandService;
    private final TransportRequestQueryService queryService;
    private final ScenarioContext context;
    private ReviewOutcome lastReview;
    private ResubmissionOutcome lastResubmission;

    public TransportRequestReviewSteps(
            TransportRequestReviewService reviewService,
            TransportRequestCommandService commandService,
            TransportRequestQueryService queryService,
            ScenarioContext context) {
        this.reviewService = reviewService;
        this.commandService = commandService;
        this.queryService = queryService;
        this.context = context;
    }

    @もし("営業担当者が版 {int} の審査を根拠 {string} で確定する")
    public void 審査を確定する(int versionNo, String rationale) {
        lastReview = reviewService.approve(
                new ApproveTransportRequestCommand(current().number(), versionNo, REVIEWER, rationale));
    }

    @もし("営業担当者が版 {int} を理由 {string} と不足事項 {string} で差し戻す")
    public void 差し戻す(int versionNo, String reason, String missingItems) {
        lastReview = reviewService.sendBack(
                new SendBackTransportRequestCommand(current().number(), versionNo, REVIEWER, reason, missingItems));
    }

    @もし("荷主が目的地を {string} に直して再提出する")
    public void 目的地を直して再提出する(String destination) {
        ShipmentTerms terms = current().currentVersion().terms();
        ShipmentTermsInput input = new ShipmentTermsInput(
                terms.consigneeCompanyId(),
                terms.origin(),
                new Location(destination),
                terms.arrivalDeadline(),
                terms.cargo().category(),
                terms.cargo().packageType(),
                terms.cargo().packageCount(),
                terms.cargo().grossWeightKg(),
                terms.cargo().volumeM3());
        lastResubmission = commandService.resubmit(
                new ResubmitTransportRequestCommand(current().number(), SHIPPER, SUBMITTER, input));
    }

    @ならば("審査は受け付けられる")
    public void 審査は受け付けられる() {
        assertThat(lastReview).isInstanceOf(ReviewOutcome.Reviewed.class);
    }

    @ならば("審査は {string} として受け付けられない")
    public void 審査は受け付けられない(String reason) {
        assertThat(lastReview)
                .isInstanceOfSatisfying(
                        ReviewOutcome.Rejected.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(REJECTIONS.get(reason)));
    }

    @ならば("審査は {string} として受け付けられず、最新の版 {int} の再審査が求められる")
    public void 古い版として受け付けられない(String reason, int latestVersionNo) {
        assertThat(lastReview).isInstanceOfSatisfying(ReviewOutcome.Rejected.class, rejected -> {
            assertThat(rejected.reason()).isEqualTo(REJECTIONS.get(reason));
            assertThat(rejected.currentVersionNo()).isEqualTo(latestVersionNo);
        });
    }

    @ならば("輸送要求は {string} になる")
    public void 輸送要求の状態(String status) {
        assertThat(current().status()).isEqualTo(STATUSES.get(status));
    }

    @ならば("版 {int} の審査記録に判断 {string} と判断者と時刻 {string} と根拠 {string} が残る")
    public void 確定の審査記録が残る(int versionNo, String decision, String decidedAt, String rationale) {
        assertThat(lastRecord()).satisfies(reviewRecord -> {
            assertThat(reviewRecord.versionNo()).isEqualTo(versionNo);
            assertThat(reviewRecord.decision()).isEqualTo(DECISIONS.get(decision));
            assertThat(reviewRecord.reviewerId()).isEqualTo(REVIEWER);
            assertThat(reviewRecord.decidedAt()).isEqualTo(new UtcInstant(Instant.parse(decidedAt)));
            assertThat(reviewRecord.rationale()).isEqualTo(rationale);
        });
    }

    @ならば("版 {int} の審査記録に判断 {string} と理由 {string} と不足事項 {string} が残る")
    public void 差戻しの審査記録が残る(int versionNo, String decision, String reason, String missingItems) {
        assertThat(lastRecord()).satisfies(reviewRecord -> {
            assertThat(reviewRecord.versionNo()).isEqualTo(versionNo);
            assertThat(reviewRecord.decision()).isEqualTo(DECISIONS.get(decision));
            assertThat(reviewRecord.rationale()).isEqualTo(reason);
            assertThat(reviewRecord.missingItems()).isEqualTo(missingItems);
        });
    }

    @ならば("再提出は受け付けられず {string} に誤り {string} が示される")
    public void 再提出は受け付けられない(String item, String reason) {
        assertThat(lastResubmission)
                .isInstanceOfSatisfying(
                        ResubmissionOutcome.Invalid.class,
                        invalid -> assertThat(invalid.violations().has(ITEMS.get(item), REASONS.get(reason)))
                                .as("%s に %s: %s", item, reason, invalid.violations())
                                .isTrue());
    }

    @ならば("業務番号は {string} のまま版 {int} になる")
    public void 業務番号は変わらず版が進む(String number, int versionNo) {
        assertThat(lastResubmission).isInstanceOfSatisfying(ResubmissionOutcome.Resubmitted.class, resubmitted -> {
            assertThat(resubmitted.number().text()).isEqualTo(number);
            assertThat(resubmitted.versionNo()).isEqualTo(versionNo);
        });
        assertThat(current().number().text()).isEqualTo(number);
        assertThat(current().currentVersion().versionNo()).isEqualTo(versionNo);
    }

    private TransportRequest current() {
        return queryService
                .findById(new TransportRequestId(context.transportRequestId()))
                .orElseThrow();
    }

    private ReviewRecord lastRecord() {
        var records = current().reviewRecords();
        assertThat(records).isNotEmpty();
        return records.getLast();
    }
}
