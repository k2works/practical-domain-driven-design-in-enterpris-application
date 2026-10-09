package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.entities.TransportRequestVersion;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipperApproval;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 予約確定に使える見積りの照会の入力ポート。見積りの公開 API のアダプター（{@code interfaces.api.internal}）が委ね、確定に要る値の
 * 写しに変える（2026-10-09 に公開 API を interfaces.api へ移した）。
 *
 * <p> * 予約確定に使える見積りの照会の実装（見積りの公開 API。ADR-016、Q-INV-06。Bolt 23）。判定は見積りの集約（{@link
 * Quotation#bookingRejectionAt}）に置き、使えるときだけ輸送要求の見積りの対象の版から確定に要る値を写す。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class BookableQuotationQueryService {

    private final QuotationRepository quotationRepository;
    private final TransportRequestRepository transportRequestRepository;

    public BookableQuotationQueryService(
            QuotationRepository quotationRepository, TransportRequestRepository transportRequestRepository) {
        this.quotationRepository = quotationRepository;
        this.transportRequestRepository = transportRequestRepository;
    }

    /**
     * 業務番号と見積り番号の見積りが、commit 時刻に予約確定に使えるかを判定する（ADR-016、B-INV-02）。
     *
     * @param transportRequestNumber 業務番号の表記
     * @param quotationNo 見積り番号
     * @param committedAt 予約確定の commit 時刻
     * @return 使えるなら見積りと対象の輸送要求、使えないなら理由
     */
    public BookableQuotation find(String transportRequestNumber, int quotationNo, UtcInstant committedAt) {
        Optional<Quotation> found = findByNumber(transportRequestNumber, quotationNo);
        if (found.isEmpty()) {
            return new BookableQuotation.NotBookable(BookableQuotation.Reason.QUOTATION_NOT_FOUND);
        }
        Quotation quotation = found.get();
        Optional<QuotationRejection> rejection = quotation.bookingRejectionAt(committedAt);
        if (rejection.isPresent()) {
            return new BookableQuotation.NotBookable(reasonOf(rejection.get()));
        }
        Optional<TransportRequest> transportRequest = transportRequestRepository
                .findById(quotation.transportRequestId())
                .filter(r -> r.currentVersion().versionNo() == quotation.transportRequestVersionNo());
        if (transportRequest.isEmpty()) {
            // 承認済みの見積りの対象の版が現在の版でないことは、再提出の規則（見積り作成中より後は再提出できない）では起きない
            return new BookableQuotation.NotBookable(BookableQuotation.Reason.NOT_APPROVED);
        }
        return bookable(quotation, transportRequest.get());
    }

    private static BookableQuotation.Bookable bookable(Quotation quotation, TransportRequest request) {
        TransportRequestVersion version = request.currentVersion();
        ShipmentTerms terms = version.terms();
        AssignedRoute route = quotation.assignedRoute().orElseThrow();
        ShipperApproval approval = quotation.shipperApproval().orElseThrow();
        return new BookableQuotation.Bookable(
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
                approval.approvedAt(),
                quotation.expiry().orElseThrow().expiresAt());
    }

    private static String summary(Cargo cargo) {
        return cargo.category().name() + " / " + cargo.packageType().name() + " × " + cargo.packageCount() + " / "
                + cargo.grossWeightKg().toPlainString() + " kg / "
                + cargo.volumeM3().toPlainString() + " m3";
    }

    /** 業務番号と見積り番号で見積りを引く（D-4。Bolt 23b）。業務番号の形でなければ見つからない。 */
    private Optional<Quotation> findByNumber(String transportRequestNumber, int quotationNo) {
        TransportRequestNumber number;
        try {
            number = TransportRequestNumber.parse(transportRequestNumber);
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
        return transportRequestRepository
                .findByNumberForStaff(number)
                .flatMap(request -> quotationRepository.findByTransportRequestIdAndNo(request.id(), quotationNo));
    }

    private static BookableQuotation.Reason reasonOf(QuotationRejection rejection) {
        return switch (rejection) {
            case EXPIRED -> BookableQuotation.Reason.EXPIRED;
            case REPLACED -> BookableQuotation.Reason.REPLACED;
            default -> BookableQuotation.Reason.NOT_APPROVED;
        };
    }
}
