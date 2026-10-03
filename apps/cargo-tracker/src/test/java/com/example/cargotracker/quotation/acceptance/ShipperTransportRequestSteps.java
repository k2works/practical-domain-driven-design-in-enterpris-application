package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SendBackTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ResubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.ReviewOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ResubmissionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.SendBackNotice;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 荷主の照会と再提出（US-02 の差戻しから版 2 まで）のステップ定義。見積りの入力ポートだけを呼ぶ（AT-05）。
 * 荷主の照会と再提出は、必ず荷主企業を渡す（Q-INV-08）。
 */
public class ShipperTransportRequestSteps {

    /** 提出したのと同じ荷主（TransportRequestSteps と同じ値）。 */
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static final UserId SUBMITTER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

    /** 他社（別の荷主企業）とその担当者。 */
    private static final CompanyId OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private static final UserId OTHER_SUBMITTER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000102"));

    /** 認証ができるまでの仮の営業担当者（TransportRequestReviewSteps と同じ値）。 */
    private static final UserId REVIEWER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301"));

    private static final Map<String, TransportRequestStatus> STATUSES = Map.of(
            "下書き", TransportRequestStatus.DRAFT,
            "審査中", TransportRequestStatus.UNDER_REVIEW,
            "見積り作成中", TransportRequestStatus.QUOTING);

    private static final Map<String, Class<? extends ResubmissionOutcome>> RESUBMISSION_FAILURES = Map.of(
            "見つからない", ResubmissionOutcome.NotFound.class,
            "下書きでない", ResubmissionOutcome.Rejected.class);

    private final TransportRequestCommandService commandService;
    private final TransportRequestReviewService reviewService;
    private final TransportRequestQueryService queryService;
    private final StaffTransportRequestQueryService staffQueryService;
    private List<TransportRequestSummary> shipperList = List.of();
    private ResubmissionOutcome lastResubmission;

    public ShipperTransportRequestSteps(
            TransportRequestCommandService commandService,
            TransportRequestReviewService reviewService,
            TransportRequestQueryService queryService,
            StaffTransportRequestQueryService staffQueryService) {
        this.commandService = commandService;
        this.reviewService = reviewService;
        this.queryService = queryService;
        this.staffQueryService = staffQueryService;
    }

    @前提("他社が見積依頼を提出している")
    public void 他社が見積依頼を提出している() {
        SubmissionOutcome outcome = commandService.submit(new SubmitTransportRequestCommand(
                OTHER_SHIPPER, OTHER_SUBMITTER, ShipmentTermsFixture.completeInput()));
        assertThat(outcome).isInstanceOf(SubmissionOutcome.Submitted.class);
    }

    @前提("営業担当者が {string} の版 {int} を理由 {string} と不足事項 {string} で差し戻す")
    public void 業務番号を指定して差し戻す(String number, int versionNo, String reason, String missingItems) {
        ReviewOutcome outcome = reviewService.sendBack(new SendBackTransportRequestCommand(
                TransportRequestNumber.parse(number), versionNo, REVIEWER, reason, missingItems));
        assertThat(outcome).isInstanceOf(ReviewOutcome.Reviewed.class);
    }

    @もし("荷主が自社の見積依頼の一覧を開く")
    public void 荷主が自社の見積依頼の一覧を開く() {
        shipperList = queryService.findSummaries(SHIPPER);
    }

    @ならば("一覧に {string} が版 {int} の {string} で出る")
    public void 一覧に版と状態で出る(String number, int versionNo, String status) {
        assertThat(rowOf(number)).hasValueSatisfying(row -> {
            assertThat(row.versionNo()).isEqualTo(versionNo);
            assertThat(row.status()).isEqualTo(STATUSES.get(status));
        });
    }

    @ならば("一覧に {string} が出る")
    public void 一覧に出る(String number) {
        assertThat(rowOf(number)).isPresent();
    }

    @ならば("一覧に {string} は出ない")
    public void 一覧に出ない(String number) {
        assertThat(rowOf(number)).isEmpty();
    }

    @ならば("荷主が {string} の詳細を開くと、差戻しの理由 {string} と不足事項 {string} が読める")
    public void 差戻しの理由と不足事項が読める(String number, String reason, String missingItems) {
        assertThat(shipperView(number).orElseThrow().sendBackNotice())
                .contains(new SendBackNotice(reason, missingItems));
    }

    @ならば("荷主が {string} の詳細を開くと、差戻しの理由は出ない")
    public void 差戻しの理由は出ない(String number) {
        assertThat(shipperView(number).orElseThrow().sendBackNotice()).isEmpty();
    }

    @ならば("荷主が {string} を照会しても見つからない")
    public void 照会しても見つからない(String number) {
        assertThat(shipperView(number)).isEmpty();
    }

    @もし("荷主が {string} の目的地を {string} に直して出し直す")
    public void 目的地を直して出し直す(String number, String destination) {
        ShipmentTerms terms = ShipmentTermsFixture.generalCargo();
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
                new ResubmitTransportRequestCommand(TransportRequestNumber.parse(number), SHIPPER, SUBMITTER, input));
    }

    @ならば("出し直しは受け付けられ、業務番号 {string} の版 {int} になる")
    public void 出し直しは受け付けられる(String number, int versionNo) {
        assertThat(lastResubmission).isInstanceOfSatisfying(ResubmissionOutcome.Resubmitted.class, resubmitted -> {
            assertThat(resubmitted.number().text()).isEqualTo(number);
            assertThat(resubmitted.versionNo()).isEqualTo(versionNo);
        });
    }

    @ならば("出し直しは {string} として受け付けられない")
    public void 出し直しは受け付けられない(String failure) {
        assertThat(lastResubmission).isInstanceOf(RESUBMISSION_FAILURES.get(failure));
        if (lastResubmission instanceof ResubmissionOutcome.Rejected rejected) {
            assertThat(rejected.reason()).isEqualTo(ResubmissionRejection.NOT_DRAFT);
        }
    }

    @ならば("営業担当者の受付一覧に {string} が版 {int} で並ぶ")
    public void 受付一覧に版で並ぶ(String number, int versionNo) {
        assertThat(staffQueryService.findUnderReview()).anySatisfy(row -> {
            assertThat(row.number().text()).isEqualTo(number);
            assertThat(row.versionNo()).isEqualTo(versionNo);
        });
    }

    private Optional<TransportRequestSummary> rowOf(String number) {
        return shipperList.stream()
                .filter(row -> row.number().text().equals(number))
                .findFirst();
    }

    private Optional<TransportRequest> shipperView(String number) {
        return queryService.findByNumber(TransportRequestNumber.parse(number), SHIPPER);
    }
}
