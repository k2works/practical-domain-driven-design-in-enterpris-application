package com.example.cargotracker.quotation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.entities.ReviewRecord;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 審査（確定・差戻し）と再提出の永続化を PostgreSQL で確かめる（US-02、Q-INV-04、ARCH-HO-01 の楽観ロック）。
 * 業務番号はほかのテストとぶつからないよう 2085 年を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisTransportRequestReviewIntegrationTest {

    private static final UserId REVIEWER = new UserId(UUID.randomUUID());
    private static final UtcInstant DECIDED_AT = new UtcInstant(Instant.parse("2085-01-05T03:00:00.654321Z"));

    @Autowired
    TransportRequestRepository repository;

    private TransportRequest saved(int sequence) {
        return saved(sequence, "2085-01-05T01:00:00.123456Z");
    }

    private TransportRequest saved(int sequence, String submittedAt) {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2085, sequence),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse(submittedAt)));
        repository.save(request);
        return repository.findById(request.id()).orElseThrow();
    }

    @Test
    void 審査の確定で状態と審査記録が保存され集約の版が進む() {
        TransportRequest request = saved(1);
        request.approve(1, REVIEWER, "契約条件を確認した", DECIDED_AT);

        repository.update(request);

        assertThat(repository.findById(request.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(TransportRequestStatus.QUOTING);
            assertThat(found.aggregateVersion()).isEqualTo(request.aggregateVersion() + 1);
            assertThat(found.reviewRecords()).singleElement().satisfies(reviewRecord -> {
                assertThat(reviewRecord.versionNo()).isEqualTo(1);
                assertThat(reviewRecord.decision()).isEqualTo(ReviewDecision.APPROVED);
                assertThat(reviewRecord.reviewerId()).isEqualTo(REVIEWER);
                assertThat(reviewRecord.rationale()).isEqualTo("契約条件を確認した");
                assertThat(reviewRecord.missingItems()).isNull();
                assertThat(reviewRecord.decidedAt()).isEqualTo(DECIDED_AT);
            });
        });
    }

    @Test
    void 差戻しと再提出で版2と2回目の審査記録が保存される() {
        TransportRequest request = saved(2);
        request.sendBack(1, REVIEWER, "目的地の確認が必要", "目的地の港", DECIDED_AT);
        repository.update(request);

        TransportRequest draft = repository.findById(request.id()).orElseThrow();
        ShipmentTerms changed = new ShipmentTerms(
                draft.currentVersion().terms().consigneeCompanyId(),
                draft.currentVersion().terms().origin(),
                new Location("DEHAM"),
                draft.currentVersion().terms().arrivalDeadline(),
                draft.currentVersion().terms().cargo());
        draft.resubmit(changed, new UserId(UUID.randomUUID()), DECIDED_AT);
        repository.update(draft);

        assertThat(repository.findById(request.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
            assertThat(found.number()).isEqualTo(new TransportRequestNumber(2085, 2));
            assertThat(found.currentVersion().versionNo()).isEqualTo(2);
            assertThat(found.currentVersion().terms()).isEqualTo(changed);
            assertThat(found.reviewRecords())
                    .singleElement()
                    .satisfies(reviewRecord ->
                            assertThat(reviewRecord.missingItems()).isEqualTo("目的地の港"));
        });
    }

    @Test
    void 同じ集約の版を2つ読んで更新すると後の更新は競合で失敗し先の更新だけが残る() {
        TransportRequest request = saved(3);
        TransportRequest first = repository.findById(request.id()).orElseThrow();
        TransportRequest second = repository.findById(request.id()).orElseThrow();
        first.approve(1, REVIEWER, "先に確定した", DECIDED_AT);
        second.sendBack(1, REVIEWER, "後から差し戻した", null, DECIDED_AT);

        repository.update(first);

        // R-27: 競合が実際に起きたこと（後の更新が失敗すること）を確かめる
        assertThatThrownBy(() -> repository.update(second))
                .isInstanceOf(ConcurrentTransportRequestUpdateException.class);
        assertThat(repository.findById(request.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(TransportRequestStatus.QUOTING);
            assertThat(found.reviewRecords())
                    .singleElement()
                    .satisfies(
                            reviewRecord -> assertThat(reviewRecord.rationale()).isEqualTo("先に確定した"));
        });
    }

    @Test
    void 社内用の照会は荷主企業で絞らずに業務番号で探せる() {
        TransportRequest request = saved(4);

        assertThat(repository.findByNumberForStaff(new TransportRequestNumber(2085, 4)))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(request.id()));
    }

    @Test
    void 受付一覧は審査中だけを最初の提出時刻の古い順に並べ差し戻して出し直した版2も最初の時刻で並ぶ() {
        TransportRequest resubmittedLater = saved(6, "2085-01-05T00:00:00Z");
        saved(7, "2085-01-05T00:30:00Z");
        TransportRequest approved = saved(8, "2085-01-05T00:10:00Z");
        approved.approve(1, REVIEWER, "確認した", DECIDED_AT);
        repository.update(approved);
        resubmittedLater.sendBack(1, REVIEWER, "直してください", null, DECIDED_AT);
        repository.update(resubmittedLater);
        TransportRequest draft = repository.findById(resubmittedLater.id()).orElseThrow();
        draft.resubmit(draft.currentVersion().terms(), new UserId(UUID.randomUUID()), DECIDED_AT);
        repository.update(draft);

        assertThat(repository.findUnderReviewSummaries())
                .filteredOn(summary -> summary.number().year() == 2085)
                .extracting(TransportRequestSummary::number, TransportRequestSummary::versionNo)
                .containsSubsequence(
                        tuple(new TransportRequestNumber(2085, 6), 2), tuple(new TransportRequestNumber(2085, 7), 1))
                .doesNotContain(tuple(new TransportRequestNumber(2085, 8), 1));
        assertThat(repository.findUnderReviewSummaries())
                .filteredOn(summary -> summary.number().equals(new TransportRequestNumber(2085, 6)))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.firstSubmittedAt())
                            .isEqualTo(new UtcInstant(Instant.parse("2085-01-05T00:00:00Z")));
                    assertThat(summary.currentSubmittedAt()).isEqualTo(DECIDED_AT);
                    assertThat(summary.cargoCategory()).isEqualTo(CargoCategory.GENERAL);
                });
    }

    @Test
    void 審査記録は判断の時刻が同じでも版番号の順に返る() {
        TransportRequest request = saved(9);
        request.sendBack(1, REVIEWER, "差し戻す", null, DECIDED_AT);
        repository.update(request);
        TransportRequest draft = repository.findById(request.id()).orElseThrow();
        draft.resubmit(draft.currentVersion().terms(), new UserId(UUID.randomUUID()), DECIDED_AT);
        repository.update(draft);
        TransportRequest underReview = repository.findById(request.id()).orElseThrow();
        underReview.approve(2, REVIEWER, "確定する", DECIDED_AT);
        repository.update(underReview);

        assertThat(repository.findById(request.id()).orElseThrow().reviewRecords())
                .extracting(ReviewRecord::versionNo)
                .containsExactly(1, 2);
    }

    @Test
    void 同じ版の2つ目の審査記録はDBの一意制約で拒否される() {
        TransportRequest request = saved(10);
        request.approve(1, REVIEWER, "確認した", DECIDED_AT);
        repository.update(request);
        TransportRequest stale = TransportRequest.reconstitute(
                request.id(),
                request.number(),
                request.shipperCompanyId(),
                TransportRequestStatus.UNDER_REVIEW,
                request.currentVersion(),
                List.of(),
                request.aggregateVersion() + 1);
        stale.sendBack(1, REVIEWER, "楽観ロックを通らない書き込み", null, DECIDED_AT);

        assertThatThrownBy(() -> repository.update(stale)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
