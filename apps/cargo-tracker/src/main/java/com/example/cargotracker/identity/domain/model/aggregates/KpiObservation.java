package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * KPI 計測記録。KPI-01 の算出に使う、輸送要求ごとの提出時刻と最初の提示時刻を記録する（KPI-INV-01）。
 * 社内の一覧で輸送要求を示すため、DE-01 の業務番号の写しを持つ（Bolt 4 より前の DE-01 から作った記録では null）。
 * 最初の提示時刻は Bolt 21 で足した。計測からの除外（PV-01）は使う Bolt で足す。
 */
@AggregateRoot
public final class KpiObservation {

    private final UUID transportRequestId;
    private final String transportRequestNumber;
    private final CompanyId shipperCompanyId;
    private final UtcInstant submittedAt;
    private UtcInstant firstPresentedAt;

    private KpiObservation(
            UUID transportRequestId,
            String transportRequestNumber,
            CompanyId shipperCompanyId,
            UtcInstant submittedAt,
            UtcInstant firstPresentedAt) {
        this.transportRequestId = Objects.requireNonNull(transportRequestId, "transportRequestId");
        this.transportRequestNumber = transportRequestNumber;
        this.shipperCompanyId = Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.firstPresentedAt = firstPresentedAt;
    }

    /**
     * 輸送要求の提出を記録する。
     */
    public static KpiObservation recordSubmission(
            UUID transportRequestId,
            String transportRequestNumber,
            CompanyId shipperCompanyId,
            UtcInstant submittedAt) {
        return new KpiObservation(transportRequestId, transportRequestNumber, shipperCompanyId, submittedAt, null);
    }

    /**
     * 保存されている状態から KPI 計測記録を組み立てる（リポジトリが使う）。最初の提示時刻は、まだ提示していなければ null。
     */
    public static KpiObservation reconstitute(
            UUID transportRequestId,
            String transportRequestNumber,
            CompanyId shipperCompanyId,
            UtcInstant submittedAt,
            UtcInstant firstPresentedAt) {
        return new KpiObservation(
                transportRequestId, transportRequestNumber, shipperCompanyId, submittedAt, firstPresentedAt);
    }

    public UUID transportRequestId() {
        return transportRequestId;
    }

    /** 業務番号の表記。Bolt 4 より前の DE-01 から作った記録では null。 */
    public String transportRequestNumber() {
        return transportRequestNumber;
    }

    public CompanyId shipperCompanyId() {
        return shipperCompanyId;
    }

    public UtcInstant submittedAt() {
        return submittedAt;
    }

    /**
     * 見積りの提示（DE-03）を記録する。最初の提示時刻はいちばん早い提示時刻とし、再見積りの提示（遅い時刻）では変えず、
     * 届く順が入れ替わって早い時刻が後から届いたときだけ書き換える（KPI-INV-01、D-11）。
     *
     * @return 最初の提示時刻を変えたら true
     * @throws IllegalArgumentException 提示時刻が提出時刻より前のとき（KPI-INV-03）
     */
    public boolean recordPresentation(UtcInstant presentedAt) {
        Objects.requireNonNull(presentedAt, "presentedAt");
        if (presentedAt.instant().isBefore(submittedAt.instant())) {
            throw new IllegalArgumentException("提示時刻が提出時刻より前: 提出 " + submittedAt + "、提示 " + presentedAt);
        }
        if (firstPresentedAt != null && !presentedAt.instant().isBefore(firstPresentedAt.instant())) {
            return false;
        }
        firstPresentedAt = presentedAt;
        return true;
    }

    /** 最初の提示時刻。まだ提示していなければ空。 */
    public Optional<UtcInstant> firstPresentedAt() {
        return Optional.ofNullable(firstPresentedAt);
    }

    /** KPI-01 のリードタイム（提出時刻から最初の提示時刻までの暦の経過時間）。まだ提示していなければ空。 */
    public Optional<Duration> leadTime() {
        return firstPresentedAt().map(presentedAt -> Duration.between(submittedAt.instant(), presentedAt.instant()));
    }

    /** 提示時刻が提出時刻より前か。骨組み（Bolt 21 の開発レビューの Red）。 */
    public boolean precedesSubmission(UtcInstant presentedAt) {
        return false;
    }
}
