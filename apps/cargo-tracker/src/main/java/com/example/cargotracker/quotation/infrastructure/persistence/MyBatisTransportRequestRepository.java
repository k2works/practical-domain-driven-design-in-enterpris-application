package com.example.cargotracker.quotation.infrastructure.persistence;

import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.entities.ReviewRecord;
import com.example.cargotracker.quotation.domain.model.entities.TransportRequestVersion;
import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 輸送要求のリポジトリの MyBatis 実装。ヘッダ・現在の版・審査記録の表を組み立てて集約にする。
 * 更新は楽観ロック（集約の版）で照合し、版と審査記録は追記専用の表に足すだけにする。
 */
@Repository
public class MyBatisTransportRequestRepository implements TransportRequestRepository {

    private final TransportRequestMapper mapper;

    public MyBatisTransportRequestRepository(TransportRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(TransportRequest transportRequest) {
        TransportRequestRow row = toRow(transportRequest);
        mapper.insertTransportRequest(row);
        mapper.insertTransportRequestVersion(row);
    }

    @Override
    public Optional<TransportRequest> findById(TransportRequestId id) {
        return mapper.selectById(id.value()).map(this::toAggregate);
    }

    @Override
    public Optional<TransportRequest> findByNumber(TransportRequestNumber number, CompanyId shipperCompanyId) {
        return mapper.selectByNumber(number.text(), shipperCompanyId.value()).map(this::toAggregate);
    }

    @Override
    public void update(TransportRequest transportRequest) {
        int updated = mapper.updateTransportRequest(
                transportRequest.id().value(),
                transportRequest.status().name(),
                transportRequest.currentVersion().versionNo(),
                transportRequest.aggregateVersion());
        if (updated == 0) {
            throw new ConcurrentTransportRequestUpdateException(
                    transportRequest.id(), transportRequest.aggregateVersion());
        }
        mapper.insertTransportRequestVersionIfAbsent(toRow(transportRequest));
        // 読み込んだ後に足した審査記録だけを追加する（往復の回数を履歴の件数に比例させない。Bolt 5 レビュー R-03）
        transportRequest
                .newReviewRecords()
                .forEach(reviewRecord -> mapper.insertReviewRecordIfAbsent(new ReviewRecordRow(
                        reviewRecord.id(),
                        transportRequest.id().value(),
                        reviewRecord.versionNo(),
                        reviewRecord.decision().name(),
                        reviewRecord.reviewerId().value(),
                        reviewRecord.rationale(),
                        reviewRecord.missingItems(),
                        reviewRecord.decidedAt().instant().atOffset(ZoneOffset.UTC))));
    }

    @Override
    public Optional<TransportRequest> findByNumberForStaff(TransportRequestNumber number) {
        return mapper.selectByNumberForStaff(number.text()).map(this::toAggregate);
    }

    @Override
    public List<TransportRequestSummary> findSummariesByShipper(CompanyId shipperCompanyId) {
        return mapper.selectSummariesByShipper(shipperCompanyId.value()).stream()
                .map(MyBatisTransportRequestRepository::toSummary)
                .toList();
    }

    @Override
    public List<TransportRequestSummary> findUnderReviewSummaries() {
        return mapper.selectUnderReviewSummaries().stream()
                .map(MyBatisTransportRequestRepository::toSummary)
                .toList();
    }

    private static TransportRequestSummary toSummary(TransportRequestSummaryRow row) {
        return new TransportRequestSummary(
                TransportRequestNumber.parse(row.requestNumber()),
                row.currentVersionNo(),
                TransportRequestStatus.valueOf(row.status()),
                new UtcInstant(row.firstSubmittedAt().toInstant()),
                new UtcInstant(row.currentSubmittedAt().toInstant()),
                new Location(row.originUnlocode()),
                new Location(row.destinationUnlocode()),
                new UtcInstant(row.arrivalDeadline().toInstant()),
                CargoCategory.valueOf(row.cargoCategory()));
    }

    private static TransportRequestRow toRow(TransportRequest request) {
        TransportRequestVersion version = request.currentVersion();
        Cargo cargo = version.terms().cargo();
        return new TransportRequestRow(
                request.id().value(),
                request.number().text(),
                request.shipperCompanyId().value(),
                request.status().name(),
                version.versionNo(),
                request.aggregateVersion(),
                version.terms().consigneeCompanyId().value(),
                version.terms().origin().unLocode(),
                version.terms().destination().unLocode(),
                version.terms().arrivalDeadline().instant().atOffset(ZoneOffset.UTC),
                cargo.category().name(),
                cargo.packageType().name(),
                cargo.packageCount(),
                cargo.grossWeightKg(),
                cargo.volumeM3(),
                version.submittedBy().value(),
                version.submittedAt().instant().atOffset(ZoneOffset.UTC));
    }

    private TransportRequest toAggregate(TransportRequestRow row) {
        TransportRequestVersion version = new TransportRequestVersion(
                row.currentVersionNo(),
                new ShipmentTerms(
                        new CompanyId(row.consigneeCompanyId()),
                        new Location(row.originUnlocode()),
                        new Location(row.destinationUnlocode()),
                        new UtcInstant(row.arrivalDeadline().toInstant()),
                        new Cargo(
                                CargoCategory.valueOf(row.cargoCategory()),
                                PackageType.valueOf(row.packageType()),
                                row.packageCount(),
                                row.grossWeightKg(),
                                row.volumeM3())),
                new UserId(row.submittedBy()),
                new UtcInstant(row.submittedAt().toInstant()));
        return TransportRequest.reconstitute(
                new TransportRequestId(row.id()),
                TransportRequestNumber.parse(row.requestNumber()),
                new CompanyId(row.shipperCompanyId()),
                TransportRequestStatus.valueOf(row.status()),
                version,
                mapper.selectReviewRecords(row.id()).stream()
                        .map(MyBatisTransportRequestRepository::toReviewRecord)
                        .toList(),
                row.version());
    }

    private static ReviewRecord toReviewRecord(ReviewRecordRow row) {
        return new ReviewRecord(
                row.id(),
                row.versionNo(),
                ReviewDecision.valueOf(row.decision()),
                new UserId(row.reviewerId()),
                row.rationale(),
                row.missingItems(),
                new UtcInstant(row.decidedAt().toInstant()));
    }
}
