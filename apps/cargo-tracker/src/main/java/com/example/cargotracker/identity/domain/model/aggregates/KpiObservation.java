package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * KPI 計測記録。KPI-01 の算出に使う、輸送要求ごとの提出時刻を記録する（KPI-INV-01）。
 * Bolt 1 では提出時刻だけを持ち、最初の提示時刻と除外の理由は後の Bolt で足す。
 */
@AggregateRoot
public class KpiObservation {

    private final UUID transportRequestId;
    private final CompanyId shipperCompanyId;
    private final UtcInstant submittedAt;
    private final boolean excluded;

    private KpiObservation(UUID transportRequestId, CompanyId shipperCompanyId, UtcInstant submittedAt,
            boolean excluded) {
        this.transportRequestId = Objects.requireNonNull(transportRequestId, "transportRequestId");
        this.shipperCompanyId = Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.excluded = excluded;
    }

    /**
     * 輸送要求の提出を記録する。記録したものは計測の対象とする。
     */
    public static KpiObservation recordSubmission(UUID transportRequestId, CompanyId shipperCompanyId,
            UtcInstant submittedAt) {
        return new KpiObservation(transportRequestId, shipperCompanyId, submittedAt, false);
    }


    /**
     * 保存されている状態から KPI 計測記録を組み立てる（リポジトリが使う）。
     */
    public static KpiObservation reconstitute(UUID transportRequestId, CompanyId shipperCompanyId,
            UtcInstant submittedAt, boolean excluded) {
        return new KpiObservation(transportRequestId, shipperCompanyId, submittedAt, excluded);
    }

    public UUID transportRequestId() {
        return transportRequestId;
    }

    public CompanyId shipperCompanyId() {
        return shipperCompanyId;
    }

    public UtcInstant submittedAt() {
        return submittedAt;
    }

    public boolean excluded() {
        return excluded;
    }
}
