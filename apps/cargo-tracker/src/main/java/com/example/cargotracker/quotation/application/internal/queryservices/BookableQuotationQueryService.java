package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.api.BookableQuotationQuery;
import com.example.cargotracker.quotation.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.api.BookableQuotationResult;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.entities.TransportRequestVersion;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipperApproval;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 予約確定に使える見積りの照会の実装（見積りの公開 API。ADR-016、Q-INV-06。Bolt 23）。判定は見積りの集約（{@link
 * Quotation#bookingRejectionAt}）に置き、使えるときだけ輸送要求の見積りの対象の版から確定に要る値を写す。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class BookableQuotationQueryService implements BookableQuotationQuery {

    private final QuotationRepository quotationRepository;
    private final TransportRequestRepository transportRequestRepository;

    public BookableQuotationQueryService(
            QuotationRepository quotationRepository, TransportRequestRepository transportRequestRepository) {
        this.quotationRepository = quotationRepository;
        this.transportRequestRepository = transportRequestRepository;
    }

    @Override
    public BookableQuotationResult find(BookableQuotationRequest request) {
        Optional<Quotation> found = quotationRepository.findById(new QuotationId(request.quotationId()));
        if (found.isEmpty()) {
            return notBookable(BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND);
        }
        Quotation quotation = found.get();
        Optional<QuotationRejection> rejection = quotation.bookingRejectionAt(request.committedAt());
        if (rejection.isPresent()) {
            return notBookable(reasonOf(rejection.get()));
        }
        Optional<TransportRequest> transportRequest = transportRequestRepository
                .findById(quotation.transportRequestId())
                .filter(r -> r.currentVersion().versionNo() == quotation.transportRequestVersionNo());
        if (transportRequest.isEmpty()) {
            // 承認済みの見積りの対象の版が現在の版でないことは、再提出の規則（見積り作成中より後は再提出できない）では起きない
            return notBookable(BookableQuotationResult.NotBookable.NOT_APPROVED);
        }
        return bookable(quotation, transportRequest.get());
    }

    private static BookableQuotationResult bookable(Quotation quotation, TransportRequest request) {
        TransportRequestVersion version = request.currentVersion();
        ShipmentTerms terms = version.terms();
        AssignedRoute route = quotation.assignedRoute().orElseThrow();
        ShipperApproval approval = quotation.shipperApproval().orElseThrow();
        return new BookableQuotationResult.Bookable(
                request.id().value(),
                version.versionNo(),
                request.number().text(),
                quotation.id().value(),
                quotation.quotationNo(),
                request.shipperCompanyId(),
                terms.consigneeCompanyId(),
                route.routingCaseNumber(),
                route.routeVersionNo(),
                terms.cargo().category().name(),
                summary(terms.cargo()),
                approval.approvedBy().value(),
                quotation.expiry().orElseThrow().expiresAt());
    }

    private static String summary(Cargo cargo) {
        return cargo.category().name() + " / " + cargo.packageType().name() + " × " + cargo.packageCount() + " / "
                + cargo.grossWeightKg().toPlainString() + " kg / "
                + cargo.volumeM3().toPlainString() + " m3";
    }

    private static String reasonOf(QuotationRejection rejection) {
        return switch (rejection) {
            case EXPIRED -> BookableQuotationResult.NotBookable.EXPIRED;
            case REPLACED -> BookableQuotationResult.NotBookable.REPLACED;
            default -> BookableQuotationResult.NotBookable.NOT_APPROVED;
        };
    }

    private static BookableQuotationResult notBookable(String reason) {
        return new BookableQuotationResult.NotBookable(reason);
    }
}
