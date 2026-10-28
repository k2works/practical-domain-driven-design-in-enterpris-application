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
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 予約確定に使える見積りの照会の入力ポート。見積りの公開 API のアダプター（{@code interfaces.api.internal}）が委ね、確定に要る値の
 * 写しに変える（2026-10-09 に公開 API を interfaces.api へ移した）。
 *
 * <p> * 予約確定に使える見積りの照会の実装（見積りの公開 API。ADR-016、Q-INV-06。Bolt 23）。判定は見積りの集約（{@link
 * Quotation#bookingRejectionAt}）に置き、使えるときだけ輸送要求の見積りの対象の版から確定に要る値を写す。操作者の担当企業の外の
 * 輸送要求は、担当範囲の判定の部品（{@link StaffTransportRequestScope}）で、ほかの理由より先に、見積りの行を押さえる前に範囲の外と
 * する（Bolt 38f2。US-16 AC4）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class BookableQuotationQueryService {

    private final QuotationRepository quotationRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final StaffTransportRequestScope scope;

    public BookableQuotationQueryService(
            QuotationRepository quotationRepository,
            TransportRequestRepository transportRequestRepository,
            StaffTransportRequestScope scope) {
        this.quotationRepository = quotationRepository;
        this.transportRequestRepository = transportRequestRepository;
        this.scope = scope;
    }

    /**
     * 業務番号と見積り番号の見積りが、commit 時刻に予約確定に使えるかを判定する（ADR-016、B-INV-02）。
     *
     * @param transportRequestNumber 業務番号の表記
     * @param quotationNo 見積り番号
     * @param committedAt 予約確定の commit 時刻
     * @param operator 操作者（担当範囲の判定に使う）
     * @return 使えるなら見積りと対象の輸送要求、範囲の外なら範囲の外、使えないなら理由
     */
    public BookableQuotation find(
            String transportRequestNumber, int quotationNo, UtcInstant committedAt, AuthenticatedActor operator) {
        return lookUp(transportRequestNumber, quotationNo, committedAt, operator, false);
    }

    /**
     * 本予約の確定のトランザクションの中で、見積りの行をそのコミットまで押さえてから {@link #find} と同じ判定をする（ADR-016、US-07 AC5。
     * Bolt 37e2）。再設計要の印を付ける更新は確定のコミットを待ち、先に付いた印は押さえた後の読み込みで見える。呼び手のトランザクションが
     * なければ例外にする（ロックを呼び手のトランザクションの終わりまで持つため）。
     *
     * @param transportRequestNumber 業務番号の表記
     * @param quotationNo 見積り番号
     * @param committedAt 予約確定の commit 時刻
     * @param operator 操作者（担当範囲の判定に使う。範囲の外なら行を押さえない）
     * @return 使えるなら見積りと対象の輸送要求、範囲の外なら範囲の外、使えないなら理由
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BookableQuotation findForConfirmation(
            String transportRequestNumber, int quotationNo, UtcInstant committedAt, AuthenticatedActor operator) {
        return lookUp(transportRequestNumber, quotationNo, committedAt, operator, true);
    }

    /**
     * 業務番号の輸送要求を担当範囲で引き当て、範囲の中なら見積りを引いて判定する（D-4。Bolt 23b・38f2）。業務番号の形でなければ見つからない。
     * 範囲の判定は失効などの判定と見積りの行を押さえることより先（範囲の外では理由を示さず、ロックも取らない）。{@code lock} なら見積りの
     * 行を押さえてから読む（Bolt 37e2）。
     */
    private BookableQuotation lookUp(
            String transportRequestNumber,
            int quotationNo,
            UtcInstant committedAt,
            AuthenticatedActor operator,
            boolean lock) {
        Optional<TransportRequestNumber> number = parse(transportRequestNumber);
        if (number.isEmpty()) {
            return new BookableQuotation.NotBookable(BookableQuotation.Reason.QUOTATION_NOT_FOUND);
        }
        return switch (scope.find(number.get(), operator)) {
            case StaffTransportRequestLookup.Found(TransportRequest request) ->
                judge(quotationOf(request, quotationNo, lock), committedAt);
            case StaffTransportRequestLookup.NotFound _ ->
                new BookableQuotation.NotBookable(BookableQuotation.Reason.QUOTATION_NOT_FOUND);
            case StaffTransportRequestLookup.OutOfScope _ ->
                new BookableQuotation.OutOfScope(number.get().text(), quotationNo);
        };
    }

    private BookableQuotation judge(Optional<Quotation> found, UtcInstant committedAt) {
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
        return CargoDisplayNames.summary(cargo);
    }

    private static Optional<TransportRequestNumber> parse(String transportRequestNumber) {
        try {
            return Optional.of(TransportRequestNumber.parse(transportRequestNumber));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    private Optional<Quotation> quotationOf(TransportRequest request, int quotationNo, boolean lock) {
        if (lock && !quotationRepository.lockForBooking(request.id(), quotationNo)) {
            return Optional.empty();
        }
        return quotationRepository.findByTransportRequestIdAndNo(request.id(), quotationNo);
    }

    private static BookableQuotation.Reason reasonOf(QuotationRejection rejection) {
        return switch (rejection) {
            case EXPIRED -> BookableQuotation.Reason.EXPIRED;
            case REPLACED -> BookableQuotation.Reason.REPLACED;
            case ROUTE_REDESIGN_REQUIRED -> BookableQuotation.Reason.ROUTE_REDESIGN_REQUIRED;
            case NOT_APPROVED -> BookableQuotation.Reason.NOT_APPROVED;
            case ROUTE_ALREADY_DEPARTED -> BookableQuotation.Reason.ROUTE_ALREADY_DEPARTED;
            // 予約確定の判定（bookingRejectionAt）は上の 4 つと出発済み（Bolt 37e4）だけを返す。足し忘れが黙って NOT_APPROVED にならないよう、既定の腕を置かず
            // 網羅の switch にする（Bolt 37e）
            case TRANSPORT_REQUEST_NOT_QUOTING,
                    TRANSPORT_REQUEST_BOOKED,
                    ALREADY_QUOTED,
                    NOT_PENDING_APPROVAL,
                    OUTDATED_VERSION,
                    NOT_PRESENTED,
                    ROUTING_REQUESTED,
                    NOT_AWAITING_SHIPPER_APPROVAL,
                    ALREADY_APPROVED,
                    APPROVED_AND_STILL_VALID -> throw new IllegalStateException("予約確定の判定が返さない理由です: " + rejection);
        };
    }
}
