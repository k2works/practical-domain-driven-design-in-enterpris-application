package com.example.cargotracker.quotation.infrastructure.persistence;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.entities.TransportRequestVersion;
import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 輸送要求のリポジトリの MyBatis 実装。ヘッダと版の表を組み立てて集約にする。
 */
@Repository
public class MyBatisTransportRequestRepository implements TransportRequestRepository {

    /** 楽観ロックの初期値。期待版の照合（ARCH-HO-01）は後の Bolt で入れる。 */
    private static final long INITIAL_LOCK_VERSION = 0L;

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
        return mapper.selectById(id.value()).map(MyBatisTransportRequestRepository::toAggregate);
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
                INITIAL_LOCK_VERSION,
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

    private static TransportRequest toAggregate(TransportRequestRow row) {
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
                version);
    }
}
