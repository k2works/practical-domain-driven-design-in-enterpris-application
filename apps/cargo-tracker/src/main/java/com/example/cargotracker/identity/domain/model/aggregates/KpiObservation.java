package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * KPI 計測記録。KPI-01 の算出に使う、輸送要求ごとの提出時刻を記録する（KPI-INV-01）。
 * 社内の一覧で輸送要求を示すため、DE-01 の業務番号の写しを持つ（Bolt 4 より前の DE-01 から作った記録では null）。
 * Bolt 1 では提出時刻だけを持ち、最初の提示時刻と計測からの除外（PV-01）は使う Bolt で足す。
 */
@AggregateRoot
public final class KpiObservation {

    private final UUID transportRequestId;
    private final String transportRequestNumber;
    private final CompanyId shipperCompanyId;
    private final UtcInstant submittedAt;

    private KpiObservation(
            UUID transportRequestId,
            String transportRequestNumber,
            CompanyId shipperCompanyId,
            UtcInstant submittedAt) {
        this.transportRequestId = Objects.requireNonNull(transportRequestId, "transportRequestId");
        this.transportRequestNumber = transportRequestNumber;
        this.shipperCompanyId = Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
    }

    /**
     * 輸送要求の提出を記録する。
     */
    public static KpiObservation recordSubmission(
            UUID transportRequestId,
            String transportRequestNumber,
            CompanyId shipperCompanyId,
            UtcInstant submittedAt) {
        return new KpiObservation(transportRequestId, transportRequestNumber, shipperCompanyId, submittedAt);
    }

    /**
     * 保存されている状態から KPI 計測記録を組み立てる（リポジトリが使う）。
     */
    public static KpiObservation reconstitute(
            UUID transportRequestId,
            String transportRequestNumber,
            CompanyId shipperCompanyId,
            UtcInstant submittedAt) {
        return new KpiObservation(transportRequestId, transportRequestNumber, shipperCompanyId, submittedAt);
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
}
