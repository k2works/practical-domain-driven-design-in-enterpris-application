package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.events.TransportRequestReviewed;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.entities.ReviewRecord;
import com.example.cargotracker.quotation.domain.model.entities.TransportRequestVersion;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
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
    private boolean versionAddedAfterLoad;

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
        Optional<ResubmissionRejection> rejection = checkResubmittable();
        if (rejection.isPresent()) {
            return rejection;
        }
        int versionNo = currentVersion.versionNo() + 1;
        currentVersion = new TransportRequestVersion(versionNo, terms, submittedBy, submittedAt);
        versionAddedAfterLoad = true;
        status = TransportRequestStatus.UNDER_REVIEW;
        domainEvents.add(
                new TransportRequestSubmitted(id.value(), versionNo, shipperCompanyId, submittedAt, number.text()));
        return Optional.empty();
    }

    /**
     * 見積りが提示されたことを受けて、見積提示済みにする（DE-03 の購読。US-03）。見積り作成中で、見積りの対象の版が現在の版のときだけ変え、
     * ほかのときは何もしない（イベントは少なくとも 1 回届くため、2 回目は変えない）。
     *
     * @param quotedVersionNo 見積りの対象の版番号
     * @return 状態を変えたら true
     */
    public boolean markQuotationPresented(int quotedVersionNo) {
        if (status != TransportRequestStatus.QUOTING || quotedVersionNo != currentVersion.versionNo()) {
            return false;
        }
        status = TransportRequestStatus.QUOTED;
        return true;
    }

    /**
     * 荷主が詳細経路設計を依頼したことを受けて、経路設計中にする（DE-16 の受け取り。US-24 AC1。Bolt 12）。
     * 見積り作成中・見積提示済みで、依頼した見積りの対象の版が現在の版のときだけ変える。DE-03 より先に DE-16 が届いても
     * 経路設計中にし、遅れて届いた DE-03 では戻らない（{@link #markQuotationPresented} は見積り作成中だけを変える）。
     *
     * @param quotedVersionNo 依頼した見積りの対象の版番号
     * @return 状態を変えたら true（すでに経路設計中なら false。冪等）
     */
    public boolean markRoutingRequested(int quotedVersionNo) {
        if ((status != TransportRequestStatus.QUOTING && status != TransportRequestStatus.QUOTED)
                || quotedVersionNo != currentVersion.versionNo()) {
            return false;
        }
        status = TransportRequestStatus.ROUTING;
        return true;
    }

    /**
     * 経路版が見積りに割り当てられたことを受けて、荷主承認待ちにする（DE-21 の受け取り。US-24 AC4。Bolt 20）。
     *
     * @param quotedVersionNo 見積りの対象の版番号
     * @return 状態を変えたら true
     */
    public boolean markAwaitingApproval(int quotedVersionNo) {
        return false;
    }

    /**
     * 荷主が見積りと経路を承認したことを受けて、予約待ちにする（DE-04 の受け取り。US-24 AC4。Bolt 20）。
     *
     * @param quotedVersionNo 見積りの対象の版番号
     * @return 状態を変えたら true
     */
    public boolean markReadyToBook(int quotedVersionNo) {
        return false;
    }

    /**
     * 再提出できるかを確かめる（Q-INV-15）。書類の中身を保存する前に確かめ、受け付けない再提出でファイルを残さないために使う。
     *
     * @return 受け付けない理由（再提出できるなら空）
     */
    public Optional<ResubmissionRejection> checkResubmittable() {
        return status == TransportRequestStatus.DRAFT ? Optional.empty() : Optional.of(ResubmissionRejection.NOT_DRAFT);
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

    /**
     * 版と書類番号で必要書類を探す。取得できるのは現在の版の書類だけで、前の版の書類番号は見つからない扱いにする
     * （前の版の書類は、引き継いでいれば現在の版にある。Bolt 6〜8 レビュー R-10・R-19）。
     */
    public Optional<RequiredDocument> document(int versionNo, int documentNo) {
        if (versionNo != currentVersion.versionNo()) {
            return Optional.empty();
        }
        return currentVersion.terms().documents().stream()
                .filter(document -> document.documentNo() == documentNo)
                .findFirst();
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

    /**
     * 読み込んだ後（または作った後）に作った版（再提出の版）。リポジトリは更新のときに、これがあるときだけ版と必要書類の行を追加する
     * （新しい事実だけを書く。Bolt 5 レビュー R-03、Bolt 6〜8 レビュー R-07）。最初の版は保存（{@code save}）が書く。
     */
    public Optional<TransportRequestVersion> newVersion() {
        return versionAddedAfterLoad ? Optional.of(currentVersion) : Optional.empty();
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
