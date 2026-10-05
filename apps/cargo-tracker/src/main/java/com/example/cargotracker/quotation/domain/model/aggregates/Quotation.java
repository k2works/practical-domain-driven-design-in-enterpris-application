package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingBasis;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationExpiry;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 見積り。審査済みの輸送要求版に対する料金根拠・有効期限・経路方針の提示（US-03。集約ルート）。
 * 作成中 → 承認待ち（算出する。Q-INV-05・17）→ 提示済み（社内承認して提示する。DE-03）の順に進む。
 *
 * <p>輸送要求と別の集約にする理由は、見積りが版ごとに複数作られ（置換）、有効期限で単独に失効するため
 * （ドメインモデル「見積りを輸送要求の中に入れない理由」）。輸送要求の状態は DE-03 を受けて別のトランザクションで変える。
 */
@AggregateRoot
public final class Quotation {

    private static final long INITIAL_AGGREGATE_VERSION = 0;

    private final QuotationId id;
    private final TransportRequestId transportRequestId;
    private final int quotationNo;
    private final int transportRequestVersionNo;
    private final long aggregateVersion;
    private final List<Object> domainEvents = new ArrayList<>();
    private QuotationStatus status;
    private PricingBasis pricingBasis;
    private QuotationExpiry expiry;
    private RoutePolicy routePolicy;
    private UserId approvedBy;
    private UtcInstant presentedAt;

    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    private Quotation(
            QuotationId id,
            TransportRequestId transportRequestId,
            int quotationNo,
            int transportRequestVersionNo,
            QuotationStatus status,
            PricingBasis pricingBasis,
            QuotationExpiry expiry,
            RoutePolicy routePolicy,
            UserId approvedBy,
            UtcInstant presentedAt,
            long aggregateVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.transportRequestId = Objects.requireNonNull(transportRequestId, "transportRequestId");
        if (quotationNo < 1 || transportRequestVersionNo < 1) {
            throw new IllegalArgumentException("見積り番号と版番号は 1 以上です: " + quotationNo + "、" + transportRequestVersionNo);
        }
        this.quotationNo = quotationNo;
        this.transportRequestVersionNo = transportRequestVersionNo;
        this.status = Objects.requireNonNull(status, "status");
        this.pricingBasis = pricingBasis;
        this.expiry = expiry;
        this.routePolicy = routePolicy;
        this.approvedBy = approvedBy;
        this.presentedAt = presentedAt;
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 見積りを作る（作成中）。見積りを作れるか（Q-INV-18）は、輸送要求とほかの見積りを読むアプリケーションサービスが確かめる。
     *
     * @param transportRequestVersionNo 見積りの対象の輸送要求の版番号（現在の版）
     * @param quotationNo 見積り番号（輸送要求の中で 1 から）
     */
    public static Quotation create(
            QuotationId id, TransportRequestId transportRequestId, int transportRequestVersionNo, int quotationNo) {
        return new Quotation(
                id,
                transportRequestId,
                quotationNo,
                transportRequestVersionNo,
                QuotationStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                INITIAL_AGGREGATE_VERSION);
    }

    /** 保存されている状態から見積りを組み立てる（リポジトリが使う）。イベントは生成しない。値のない項目は null。 */
    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    public static Quotation reconstitute(
            QuotationId id,
            TransportRequestId transportRequestId,
            int quotationNo,
            int transportRequestVersionNo,
            QuotationStatus status,
            PricingBasis pricingBasis,
            QuotationExpiry expiry,
            RoutePolicy routePolicy,
            UserId approvedBy,
            UtcInstant presentedAt,
            long aggregateVersion) {
        return new Quotation(
                id,
                transportRequestId,
                quotationNo,
                transportRequestVersionNo,
                status,
                pricingBasis,
                expiry,
                routePolicy,
                approvedBy,
                presentedAt,
                aggregateVersion);
    }

    /**
     * 算出する（作成中 → 承認待ち）。入力を算出の時刻で検証し（Q-INV-05・17）、違反があれば作成中のまま違反を返す。
     *
     * @return 違反（算出したら空）
     * @throws IllegalStateException 作成中でない（算出し直しは入れていない）
     */
    public Optional<QuotationViolations> calculate(QuotationInput input, UtcInstant calculatedAt) {
        if (status != QuotationStatus.DRAFT) {
            throw new IllegalStateException("作成中でない見積りは算出できません: " + status);
        }
        return switch (input.validate(calculatedAt)) {
            case QuotationInput.Invalid(QuotationViolations violations) -> Optional.of(violations);
            case QuotationInput.Valid(PricingBasis basis, QuotationExpiry validExpiry, RoutePolicy policy) -> {
                pricingBasis = basis;
                expiry = validExpiry;
                routePolicy = policy;
                status = QuotationStatus.PENDING_APPROVAL;
                yield Optional.empty();
            }
        };
    }

    /**
     * 社内承認して提示する（承認待ち → 提示済み）。承認者と提示時刻を記録し（KPI-01）、DE-03 を生成する。
     * 承認者は、認証（US-18）ができるまで仮の営業担当者（2026-10-05 の決定）。
     *
     * @return 受け付けなかった理由（受け付けたら空）
     */
    public Optional<QuotationRejection> presentInternally(UserId approver, UtcInstant at) {
        Objects.requireNonNull(approver, "approver");
        Objects.requireNonNull(at, "at");
        if (status != QuotationStatus.PENDING_APPROVAL) {
            return Optional.of(QuotationRejection.NOT_PENDING_APPROVAL);
        }
        approvedBy = approver;
        presentedAt = at;
        status = QuotationStatus.PRESENTED;
        domainEvents.add(new QuotationPresented(
                id.value(),
                quotationNo,
                transportRequestId.value(),
                transportRequestVersionNo,
                expiry.expiresAt(),
                routePolicy.via().stream().map(Location::unLocode).toList(),
                routePolicy.departureAt(),
                routePolicy.arrivalAt(),
                at));
        return Optional.empty();
    }

    /** 作成中・承認待ち・提示済みか（1 つの輸送要求に 1 つだけ。Q-INV-18）。 */
    public boolean isActive() {
        return status == QuotationStatus.DRAFT
                || status == QuotationStatus.PENDING_APPROVAL
                || status == QuotationStatus.PRESENTED;
    }

    public QuotationId id() {
        return id;
    }

    public TransportRequestId transportRequestId() {
        return transportRequestId;
    }

    public int quotationNo() {
        return quotationNo;
    }

    public int transportRequestVersionNo() {
        return transportRequestVersionNo;
    }

    public QuotationStatus status() {
        return status;
    }

    public Optional<PricingBasis> pricingBasis() {
        return Optional.ofNullable(pricingBasis);
    }

    public Optional<QuotationExpiry> expiry() {
        return Optional.ofNullable(expiry);
    }

    public Optional<RoutePolicy> routePolicy() {
        return Optional.ofNullable(routePolicy);
    }

    /** 社内承認者（提示済みのとき）。 */
    public Optional<UserId> approvedBy() {
        return Optional.ofNullable(approvedBy);
    }

    /** 提示時刻（提示済みのとき。社内承認の時刻と同じ）。 */
    public Optional<UtcInstant> presentedAt() {
        return Optional.ofNullable(presentedAt);
    }

    /** 楽観ロックの版（読み込んだときの集約の版）。リポジトリが更新のときに照合する。 */
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
