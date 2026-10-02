package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.entities.TransportRequestVersion;
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

/**
 * 輸送要求。荷主が見積りを依頼する輸送条件のまとまりで、本予約までの版を持つ。
 */
@AggregateRoot
public final class TransportRequest {

    private static final int FIRST_VERSION_NO = 1;

    private final TransportRequestId id;
    private final TransportRequestNumber number;
    private final CompanyId shipperCompanyId;
    private final TransportRequestStatus status;
    private final TransportRequestVersion currentVersion;
    private final List<Object> domainEvents = new ArrayList<>();

    private TransportRequest(
            TransportRequestId id,
            TransportRequestNumber number,
            CompanyId shipperCompanyId,
            TransportRequestStatus status,
            TransportRequestVersion currentVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.number = Objects.requireNonNull(number, "number");
        this.shipperCompanyId = Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        this.status = Objects.requireNonNull(status, "status");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
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
        TransportRequest request =
                new TransportRequest(id, number, shipperCompanyId, TransportRequestStatus.UNDER_REVIEW, firstVersion);
        request.domainEvents.add(new TransportRequestSubmitted(
                id.value(), FIRST_VERSION_NO, shipperCompanyId, submittedAt, number.text()));
        return request;
    }

    /**
     * 保存されている状態から輸送要求を組み立てる（リポジトリが使う）。イベントは生成しない。
     */
    public static TransportRequest reconstitute(
            TransportRequestId id,
            TransportRequestNumber number,
            CompanyId shipperCompanyId,
            TransportRequestStatus status,
            TransportRequestVersion currentVersion) {
        return new TransportRequest(id, number, shipperCompanyId, status, currentVersion);
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

    public List<Object> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
