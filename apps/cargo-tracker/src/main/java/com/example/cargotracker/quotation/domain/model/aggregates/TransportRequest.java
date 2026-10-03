package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.events.TransportRequestReviewed;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.entities.ReviewRecord;
import com.example.cargotracker.quotation.domain.model.entities.TransportRequestVersion;
import com.example.cargotracker.quotation.domain.model.valueobjects.ResubmissionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.SendBackNotice;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 輸送要求。荷主が見積りを依頼する輸送条件のまとまりで、本予約までの版と審査記録を持つ。
 * 受け付けない操作（審査中でない、古い版、根拠がないなど）は、利用者が受け止める業務の結果として理由を値で返す。
 */
@AggregateRoot
public final class TransportRequest {

    private static final int FIRST_VERSION_NO = 1;

    /** 根拠・理由・不足事項の文字数の上限（Q-INV-14、データモデルの VARCHAR(4000)）。文字（コードポイント）で数える（D-22）。 */
    private static final int MAX_TEXT_LENGTH = 4000;

    /** 新しく作った集約の、楽観ロックの版の初期値。 */
    private static final long INITIAL_AGGREGATE_VERSION = 0L;

    private final TransportRequestId id;
    private final TransportRequestNumber number;
    private final CompanyId shipperCompanyId;
    private final long aggregateVersion;
    private final List<ReviewRecord> reviewRecords;
    private final List<ReviewRecord> newReviewRecords = new ArrayList<>();
    private final List<Object> domainEvents = new ArrayList<>();
    private TransportRequestStatus status;
    private TransportRequestVersion currentVersion;

    private TransportRequest(
            TransportRequestId id,
            TransportRequestNumber number,
            CompanyId shipperCompanyId,
            TransportRequestStatus status,
            TransportRequestVersion currentVersion,
            List<ReviewRecord> reviewRecords,
            long aggregateVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.number = Objects.requireNonNull(number, "number");
        this.shipperCompanyId = Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        this.status = Objects.requireNonNull(status, "status");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
        this.reviewRecords = new ArrayList<>(reviewRecords);
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 輸送要求を提出する。最初の版を作って審査中にし、DE-01 を生成する。
     * 業務番号は初回の提出で振ったものを受け取り、以後変えない（Q-INV-13）。
     */
    public static TransportRequest submit(
            TransportRequestId id,
            TransportRequestNumber number,
            CompanyId shipperCompanyId,
            ShipmentTerms terms,
            UserId submittedBy,
            UtcInstant submittedAt) {
        TransportRequestVersion firstVersion =
                new TransportRequestVersion(FIRST_VERSION_NO, terms, submittedBy, submittedAt);
        TransportRequest request = new TransportRequest(
                id,
                number,
                shipperCompanyId,
                TransportRequestStatus.UNDER_REVIEW,
                firstVersion,
                List.of(),
                INITIAL_AGGREGATE_VERSION);
        request.domainEvents.add(new TransportRequestSubmitted(
                id.value(), FIRST_VERSION_NO, shipperCompanyId, submittedAt, number.text()));
        return request;
    }

    /**
     * 保存されている状態から輸送要求を組み立てる（リポジトリが使う）。イベントは生成しない。
     *
     * @param aggregateVersion 楽観ロックの版（保存したときの集約の版。ARCH-HO-01 の期待版）
     */
    public static TransportRequest reconstitute(
            TransportRequestId id,
            TransportRequestNumber number,
            CompanyId shipperCompanyId,
            TransportRequestStatus status,
            TransportRequestVersion currentVersion,
            List<ReviewRecord> reviewRecords,
            long aggregateVersion) {
        return new TransportRequest(
                id, number, shipperCompanyId, status, currentVersion, reviewRecords, aggregateVersion);
    }

    /**
     * 審査を確定する（US-02 AC1）。審査中で、対象の版が現在の版のときだけ行え（Q-INV-04・14）、見積り作成中にする。
     *
     * @return 受け付けなかった理由（受け付けたら空）
     */
    public Optional<ReviewRejection> approve(
            int targetVersionNo, UserId reviewer, String rationale, UtcInstant decidedAt) {
        return review(targetVersionNo, ReviewDecision.APPROVED, reviewer, rationale, null, decidedAt);
    }

    /**
     * 差し戻す（US-02 AC2）。審査中で、対象の版が現在の版のときだけ行え（Q-INV-04・14）、下書きにする。
     * 不足事項は任意で、空白だけなら記録しない。
     *
     * @return 受け付けなかった理由（受け付けたら空）
     */
    public Optional<ReviewRejection> sendBack(
            int targetVersionNo, UserId reviewer, String reason, String missingItems, UtcInstant decidedAt) {
        return review(targetVersionNo, ReviewDecision.SENT_BACK, reviewer, reason, missingItems, decidedAt);
    }

    /**
     * 再提出する（Q-INV-15）。下書きのときだけ行え、版番号を 1 増やした新しい版を作って審査中にし、DE-01 を生成する。
     * 輸送条件は、提出と同じ検証（輸送条件の入力の検証）を通したものを受け取る。業務番号は変えない（Q-INV-13）。
     *
     * @return 受け付けなかった理由（受け付けたら空）
     */
    public Optional<ResubmissionRejection> resubmit(ShipmentTerms terms, UserId submittedBy, UtcInstant submittedAt) {
        if (status != TransportRequestStatus.DRAFT) {
            return Optional.of(ResubmissionRejection.NOT_DRAFT);
        }
        int versionNo = currentVersion.versionNo() + 1;
        currentVersion = new TransportRequestVersion(versionNo, terms, submittedBy, submittedAt);
        status = TransportRequestStatus.UNDER_REVIEW;
        domainEvents.add(
                new TransportRequestSubmitted(id.value(), versionNo, shipperCompanyId, submittedAt, number.text()));
        return Optional.empty();
    }

    private Optional<ReviewRejection> review(
            int targetVersionNo,
            ReviewDecision decision,
            UserId reviewer,
            String rationale,
            String missingItems,
            UtcInstant decidedAt) {
        if (status != TransportRequestStatus.UNDER_REVIEW) {
            return Optional.of(ReviewRejection.NOT_UNDER_REVIEW);
        }
        if (targetVersionNo != currentVersion.versionNo()) {
            return Optional.of(ReviewRejection.STALE_VERSION);
        }
        String normalizedRationale = blankToNull(rationale);
        String normalizedMissingItems = blankToNull(missingItems);
        if (normalizedRationale == null) {
            return Optional.of(ReviewRejection.RATIONALE_REQUIRED);
        }
        if (tooLong(normalizedRationale)) {
            return Optional.of(ReviewRejection.RATIONALE_TOO_LONG);
        }
        if (tooLong(normalizedMissingItems)) {
            return Optional.of(ReviewRejection.MISSING_ITEMS_TOO_LONG);
        }
        ReviewRecord reviewRecord = new ReviewRecord(
                UUID.randomUUID(),
                targetVersionNo,
                decision,
                reviewer,
                normalizedRationale,
                normalizedMissingItems,
                decidedAt);
        reviewRecords.add(reviewRecord);
        newReviewRecords.add(reviewRecord);
        status = decision == ReviewDecision.APPROVED ? TransportRequestStatus.QUOTING : TransportRequestStatus.DRAFT;
        domainEvents.add(
                new TransportRequestReviewed(id.value(), targetVersionNo, decision.name(), reviewer, decidedAt));
        return Optional.empty();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static boolean tooLong(String text) {
        return text != null && text.codePointCount(0, text.length()) > MAX_TEXT_LENGTH;
    }

    public TransportRequestId id() {
        return id;
    }

    public TransportRequestNumber number() {
        return number;
    }

    public CompanyId shipperCompanyId() {
        return shipperCompanyId;
    }

    public TransportRequestStatus status() {
        return status;
    }

    public TransportRequestVersion currentVersion() {
        return currentVersion;
    }

    /** 審査記録（版番号の順）。 */
    public List<ReviewRecord> reviewRecords() {
        return List.copyOf(reviewRecords);
    }

    /**
     * 荷主に見せる差戻しの知らせ。差し戻されて下書きのとき、現在の版への差戻しの理由と不足事項を返す。
     * 出し直して新しい版になったら、前の版への差戻しは示さない。
     */
    public Optional<SendBackNotice> sendBackNotice() {
        if (status != TransportRequestStatus.DRAFT) {
            return Optional.empty();
        }
        return reviewRecords.reversed().stream()
                .filter(reviewRecord -> reviewRecord.versionNo() == currentVersion.versionNo())
                .filter(reviewRecord -> reviewRecord.decision() == ReviewDecision.SENT_BACK)
                .findFirst()
                .map(reviewRecord -> new SendBackNotice(reviewRecord.rationale(), reviewRecord.missingItems()));
    }

    /** 読み込んだ後（または作った後）に足した審査記録。リポジトリは更新のときに、これだけを追加する。 */
    public List<ReviewRecord> newReviewRecords() {
        return List.copyOf(newReviewRecords);
    }

    /**
     * 楽観ロックの版（読み込んだときの集約の版）。リポジトリが更新のときに照合する。
     * 更新しても、このインスタンスの版は変わらない。1 つのトランザクションで読み込んだ集約は 1 回だけ更新し、
     * もう一度変えるときは読み込み直す（同じインスタンスで 2 回更新すると、2 回目は競合として失敗する）。
     */
    public long aggregateVersion() {
        return aggregateVersion;
    }

    public List<Object> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
