package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.events.TransportRequestReviewed;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.entities.ReviewRecord;
import com.example.cargotracker.quotation.domain.model.valueobjects.ResubmissionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 輸送要求の審査（確定・差戻し）と再提出（US-02、Q-INV-04・14・15）。
 */
class TransportRequestReviewTest {

    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());
    private static final UserId REVIEWER = new UserId(UUID.randomUUID());
    private static final UtcInstant SUBMITTED_AT = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));
    private static final UtcInstant DECIDED_AT = new UtcInstant(Instant.parse("2026-10-05T03:00:00Z"));
    private static final UtcInstant RESUBMITTED_AT = new UtcInstant(Instant.parse("2026-10-05T05:00:00Z"));

    private final TransportRequestId id = new TransportRequestId(UUID.randomUUID());
    private final TransportRequestNumber number = new TransportRequestNumber(2026, 1);
    private final CompanyId shipper = new CompanyId(UUID.randomUUID());
    private TransportRequest request;

    @BeforeEach
    void 審査中の輸送要求を用意する() {
        request = TransportRequest.submit(
                id, number, shipper, ShipmentTermsFixture.generalCargo(), SHIPPER_USER, SUBMITTED_AT);
        request.clearDomainEvents();
    }

    @Test
    void 審査中の現在の版の確定で見積り作成中になり判断者と時刻と根拠が記録されDE02が出る() {
        assertThat(request.approve(1, REVIEWER, "契約条件と輸送条件を確認した", DECIDED_AT)).isEmpty();

        assertThat(request.status()).isEqualTo(TransportRequestStatus.QUOTING);
        assertThat(request.reviewRecords()).singleElement().satisfies(reviewRecord -> {
            assertThat(reviewRecord.versionNo()).isEqualTo(1);
            assertThat(reviewRecord.decision()).isEqualTo(ReviewDecision.APPROVED);
            assertThat(reviewRecord.reviewerId()).isEqualTo(REVIEWER);
            assertThat(reviewRecord.rationale()).isEqualTo("契約条件と輸送条件を確認した");
            assertThat(reviewRecord.missingItems()).isNull();
            assertThat(reviewRecord.decidedAt()).isEqualTo(DECIDED_AT);
        });
        assertThat(request.domainEvents())
                .containsExactly(new TransportRequestReviewed(id.value(), 1, "APPROVED", REVIEWER, DECIDED_AT));
    }

    @Test
    void 差し戻すと下書きになり理由と不足事項が記録される() {
        assertThat(request.sendBack(1, REVIEWER, "契約条件の確認が必要", "取引条件書", DECIDED_AT))
                .isEmpty();

        assertThat(request.status()).isEqualTo(TransportRequestStatus.DRAFT);
        assertThat(request.reviewRecords()).singleElement().satisfies(reviewRecord -> {
            assertThat(reviewRecord.decision()).isEqualTo(ReviewDecision.SENT_BACK);
            assertThat(reviewRecord.rationale()).isEqualTo("契約条件の確認が必要");
            assertThat(reviewRecord.missingItems()).isEqualTo("取引条件書");
        });
        assertThat(request.domainEvents())
                .containsExactly(new TransportRequestReviewed(id.value(), 1, "SENT_BACK", REVIEWER, DECIDED_AT));
    }

    @Test
    void 不足事項は任意で空白だけなら記録しない() {
        request.sendBack(1, REVIEWER, "書類の不足", "  ", DECIDED_AT);

        assertThat(request.reviewRecords())
                .singleElement()
                .extracting(ReviewRecord::missingItems)
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 根拠のない確定と理由のない差戻しはできない(String blank) {
        assertThat(request.approve(1, REVIEWER, blank, DECIDED_AT)).contains(ReviewRejection.RATIONALE_REQUIRED);
        assertThat(request.sendBack(1, REVIEWER, blank, null, DECIDED_AT)).contains(ReviewRejection.RATIONALE_REQUIRED);
        assertThat(request.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
        assertThat(request.reviewRecords()).isEmpty();
        assertThat(request.domainEvents()).isEmpty();
    }

    @Test
    void 根拠と理由と不足事項は4000文字までで超えた欄を理由で示す() {
        String justFits = "あ".repeat(4000);
        String tooLong = "あ".repeat(4001);

        assertThat(request.approve(1, REVIEWER, tooLong, DECIDED_AT)).contains(ReviewRejection.RATIONALE_TOO_LONG);
        assertThat(request.sendBack(1, REVIEWER, tooLong, null, DECIDED_AT))
                .contains(ReviewRejection.RATIONALE_TOO_LONG);
        assertThat(request.sendBack(1, REVIEWER, "理由", tooLong, DECIDED_AT))
                .contains(ReviewRejection.MISSING_ITEMS_TOO_LONG);
        assertThat(request.sendBack(1, REVIEWER, justFits, justFits, DECIDED_AT))
                .isEmpty();
    }

    @Test
    void 文字数は文字で数えサロゲートペアの文字も1文字とする() {
        String fourThousandCharacters = "𠮷".repeat(4000);

        assertThat(fourThousandCharacters.length()).isEqualTo(8000);
        assertThat(request.approve(1, REVIEWER, fourThousandCharacters, DECIDED_AT))
                .isEmpty();
    }

    @Test
    void 根拠の前後の空白は除いてから記録し文字数を数える() {
        assertThat(request.approve(1, REVIEWER, "  " + "あ".repeat(4000) + "  ", DECIDED_AT))
                .isEmpty();

        assertThat(request.reviewRecords())
                .singleElement()
                .satisfies(reviewRecord -> assertThat(reviewRecord.rationale()).hasSize(4000));
    }

    @Test
    void 下書きのときは確定できない() {
        request.sendBack(1, REVIEWER, "差し戻す", null, DECIDED_AT);

        assertThat(request.approve(1, REVIEWER, "確定する", DECIDED_AT)).contains(ReviewRejection.NOT_UNDER_REVIEW);
    }

    @Test
    void 新しい審査記録だけを保存の対象として区別する() {
        request.sendBack(1, REVIEWER, "差し戻す", null, DECIDED_AT);
        TransportRequest restored = TransportRequest.reconstitute(
                id, number, shipper, request.status(), request.currentVersion(), request.reviewRecords(), 1L);
        restored.resubmit(changedTerms(), SHIPPER_USER, RESUBMITTED_AT);

        restored.approve(2, REVIEWER, "確認した", RESUBMITTED_AT);

        assertThat(restored.reviewRecords()).extracting(ReviewRecord::versionNo).containsExactly(1, 2);
        assertThat(restored.newReviewRecords())
                .extracting(ReviewRecord::versionNo)
                .containsExactly(2);
    }

    @Test
    void 審査中でなければ確定も差戻しもできない() {
        request.approve(1, REVIEWER, "確認した", DECIDED_AT);

        assertThat(request.approve(1, REVIEWER, "もう一度", DECIDED_AT)).contains(ReviewRejection.NOT_UNDER_REVIEW);
        assertThat(request.sendBack(1, REVIEWER, "やはり差し戻す", null, DECIDED_AT))
                .contains(ReviewRejection.NOT_UNDER_REVIEW);
        assertThat(request.status()).isEqualTo(TransportRequestStatus.QUOTING);
    }

    @Test
    void 差戻しの後に再提出すると版2が審査中になり業務番号は変わらずDE01が出る() {
        request.sendBack(1, REVIEWER, "目的地の確認が必要", null, DECIDED_AT);
        request.clearDomainEvents();
        ShipmentTerms changed = changedTerms();

        assertThat(request.resubmit(changed, SHIPPER_USER, RESUBMITTED_AT)).isEmpty();

        assertThat(request.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
        assertThat(request.number()).isEqualTo(number);
        assertThat(request.currentVersion().versionNo()).isEqualTo(2);
        assertThat(request.currentVersion().terms()).isEqualTo(changed);
        assertThat(request.currentVersion().submittedAt()).isEqualTo(RESUBMITTED_AT);
        assertThat(request.domainEvents())
                .containsExactly(new TransportRequestSubmitted(id.value(), 2, shipper, RESUBMITTED_AT, number.text()));
    }

    @Test
    void 下書きでなければ再提出できない() {
        assertThat(request.resubmit(changedTerms(), SHIPPER_USER, RESUBMITTED_AT))
                .contains(ResubmissionRejection.NOT_DRAFT);
        assertThat(request.currentVersion().versionNo()).isEqualTo(1);
    }

    @Test
    void 再提出で版2ができた後に版1の確定と差戻しは古い版として拒否される() {
        request.sendBack(1, REVIEWER, "目的地の確認が必要", null, DECIDED_AT);
        request.resubmit(changedTerms(), SHIPPER_USER, RESUBMITTED_AT);

        assertThat(request.approve(1, REVIEWER, "版 1 を確認した", RESUBMITTED_AT)).contains(ReviewRejection.STALE_VERSION);
        assertThat(request.sendBack(1, REVIEWER, "版 1 を差し戻す", null, RESUBMITTED_AT))
                .contains(ReviewRejection.STALE_VERSION);
        assertThat(request.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
        assertThat(request.approve(2, REVIEWER, "版 2 を確認した", RESUBMITTED_AT)).isEmpty();
    }

    @Test
    void 保存された状態から審査記録と集約の版とともに組み立て直せる() {
        request.approve(1, REVIEWER, "確認した", DECIDED_AT);

        TransportRequest restored = TransportRequest.reconstitute(
                id, number, shipper, request.status(), request.currentVersion(), request.reviewRecords(), 3L);

        assertThat(restored.reviewRecords()).isEqualTo(request.reviewRecords());
        assertThat(restored.aggregateVersion()).isEqualTo(3L);
        assertThat(restored.domainEvents()).isEmpty();
    }

    private static ShipmentTerms changedTerms() {
        ShipmentTerms original = ShipmentTermsFixture.generalCargo();
        return new ShipmentTerms(
                original.consigneeCompanyId(),
                original.origin(),
                new Location("DEHAM"),
                original.arrivalDeadline(),
                original.cargo());
    }
}
